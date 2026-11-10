#include "RFDeviceSetup.h"
#include "RFCoreLocation.h"
#include "idevice.h"
#include <arpa/inet.h>
#include <errno.h>
#include <poll.h>
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <time.h>
#include <unistd.h>

struct RFPairingHost {
    struct PairableHostHandle *native;
    char *service_name;
    uint8_t *txt;
    uintptr_t txt_length;
    int listener;
    int connection;
    uint16_t port;
    int cancelled;
    pthread_mutex_t lock;
};

static int fail(struct IdeviceFfiError *error, const char *stage, char *message, size_t capacity) {
    if (!error) return 0;
    int code = error->code ? error->code : -1;
    if (message && capacity) snprintf(message, capacity, "%s（错误 %d）", stage, code);
    idevice_error_free(error);
    return code;
}
static int invalid(const char *stage, char *message, size_t capacity) {
    if (message && capacity) snprintf(message, capacity, "%s", stage);
    return -1;
}
static int cancelled(RFPairingHost *host) {
    pthread_mutex_lock(&host->lock);
    int value = host->cancelled;
    pthread_mutex_unlock(&host->lock);
    return value;
}
void rf_pair_host_cancel(RFPairingHost *host) {
    if (!host) return;
    pthread_mutex_lock(&host->lock);
    host->cancelled = 1;
    if (host->connection >= 0) shutdown(host->connection, SHUT_RDWR);
    if (host->listener >= 0) shutdown(host->listener, SHUT_RDWR);
    pthread_mutex_unlock(&host->lock);
}
void rf_pair_host_free(RFPairingHost *host) {
    if (!host) return;
    if (host->connection >= 0) close(host->connection);
    if (host->listener >= 0) close(host->listener);
    if (host->native) pairable_host_free(host->native);
    if (host->service_name) idevice_string_free(host->service_name);
    if (host->txt) idevice_data_free(host->txt, host->txt_length);
    pthread_mutex_destroy(&host->lock);
    free(host);
}
int rf_pair_host_create(RFPairingHost **out, char *message, size_t capacity) {
    if (!out) return invalid("配对配置不完整", message, capacity);
    *out = NULL;
    RFPairingHost *host = calloc(1, sizeof(*host));
    if (!host) return invalid("无法创建配对会话", message, capacity);
    host->listener = -1;
    host->connection = -1;
    if (pthread_mutex_init(&host->lock, NULL)) { free(host); return invalid("无法创建配对锁", message, capacity); }
    int code = fail(pairable_host_prepare("轨迹导航", "Mac17,7", false,
        &host->native, &host->service_name, &host->txt, &host->txt_length, NULL),
        "创建本机配对身份失败", message, capacity);
    if (code) goto failed;
    host->listener = socket(AF_INET6, SOCK_STREAM, 0);
    if (host->listener < 0) { code = invalid("无法打开配对端口", message, capacity); goto failed; }
    int no = 0;
    setsockopt(host->listener, IPPROTO_IPV6, IPV6_V6ONLY, &no, sizeof(no));
    struct sockaddr_in6 address;
    memset(&address, 0, sizeof(address));
#ifdef __APPLE__
    address.sin6_len = sizeof(address);
#endif
    address.sin6_family = AF_INET6;
    address.sin6_addr = in6addr_any;
    if (bind(host->listener, (struct sockaddr *)&address, sizeof(address)) || listen(host->listener, 1)) {
        code = invalid("无法开启本机配对监听", message, capacity); goto failed;
    }
    socklen_t size = sizeof(address);
    if (getsockname(host->listener, (struct sockaddr *)&address, &size)) {
        code = invalid("无法读取配对端口", message, capacity); goto failed;
    }
    host->port = ntohs(address.sin6_port);
    *out = host;
    return 0;
failed:
    rf_pair_host_free(host);
    return code;
}
const char *rf_pair_host_service_name(RFPairingHost *host) { return host ? host->service_name : NULL; }
uint16_t rf_pair_host_port(RFPairingHost *host) { return host ? host->port : 0; }
const uint8_t *rf_pair_host_txt(RFPairingHost *host, size_t *length) {
    if (!host || !length) return NULL;
    *length = host->txt_length;
    return host->txt;
}
int rf_pair_host_accept(RFPairingHost *host, const char *output_path,
                       RFPairingPinCallback callback, void *context,
                       char *message, size_t capacity) {
    if (!host || !output_path) return invalid("配对配置不完整", message, capacity);
    struct timespec started, now;
    clock_gettime(CLOCK_MONOTONIC, &started);
    while (!cancelled(host)) {
        clock_gettime(CLOCK_MONOTONIC, &now);
        if (now.tv_sec - started.tv_sec >= 180) return invalid("配对等待超时，请重试", message, capacity);
        struct pollfd descriptor = { .fd = host->listener, .events = POLLIN };
        int result = poll(&descriptor, 1, 200);
        if (result < 0 && errno == EINTR) continue;
        if (cancelled(host)) break;
        if (result < 0 || (result > 0 && !(descriptor.revents & POLLIN)))
            return invalid("配对连接中断，请重试", message, capacity);
        if (result == 0) continue;
        int fd = accept(host->listener, NULL, NULL);
        if (fd < 0) {
            if (errno == EINTR) continue;
            return invalid("无法接受配对连接", message, capacity);
        }
        pthread_mutex_lock(&host->lock);
        if (host->cancelled) { pthread_mutex_unlock(&host->lock); close(fd); break; }
        host->connection = fd;
        pthread_mutex_unlock(&host->lock);
        struct RpPairingFileHandle *pairing = NULL;
        idevice_set_global_timeout(120);
        int code = fail(pairable_host_accept_fd(host->native, fd, callback, context, NULL, &pairing),
                        "本机配对未完成", message, capacity);
        idevice_set_global_timeout(10);
        pthread_mutex_lock(&host->lock);
        host->connection = -1;
        pthread_mutex_unlock(&host->lock);
        close(fd);
        if (!code && !pairing) code = invalid("配对未返回有效资料", message, capacity);
        if (!code && !cancelled(host)) {
            code = fail(rp_pairing_file_write(pairing, output_path), "保存配对资料失败", message, capacity);
        } else if (!code) { code = invalid("已取消配对", message, capacity); }
        if (pairing) rp_pairing_file_free(pairing);
        return code;
    }
    return invalid("已取消配对", message, capacity);
}

struct SetupTransport { struct AdapterHandle *adapter; struct RsdHandshakeHandle *handshake; };
static void transport_close(struct SetupTransport *transport) {
    if (transport->handshake) rsd_handshake_free(transport->handshake);
    if (transport->adapter) {
        struct IdeviceFfiError *error = adapter_close(transport->adapter);
        if (error) idevice_error_free(error);
        adapter_free(transport->adapter);
    }
}
static int transport_open(uint16_t port, const char *path, struct SetupTransport *out,
                          char *message, size_t capacity) {
    if (!port || !path) return invalid("请先完成配对并填写连接端口", message, capacity);
    struct RpPairingFileHandle *pairing = NULL;
    int code = fail(rp_pairing_file_read(path, &pairing), "读取配对资料失败", message, capacity);
    if (code) return code;
    struct sockaddr_in address;
    memset(&address, 0, sizeof(address));
#ifdef __APPLE__
    address.sin_len = sizeof(address);
#endif
    address.sin_family = AF_INET;
    address.sin_port = htons(port);
    inet_pton(AF_INET, "10.7.0.1", &address.sin_addr);
    idevice_set_global_timeout(10);
    code = fail(tunnel_create_rppairing((const idevice_sockaddr *)&address, sizeof(address),
        "RouteForge Setup", pairing, NULL, NULL, &out->adapter, &out->handshake),
        "本机通道未连接，请检查 Wi-Fi、LocalDevVPN 和配对", message, capacity);
    rp_pairing_file_free(pairing);
    return code;
}
int rf_setup_probe(uint16_t port, const char *pairing_path, int *ready, char *message, size_t capacity) {
    if (!ready) return invalid("检查配置不完整", message, capacity);
    *ready = 0;
    struct SetupTransport transport = {0};
    int code = transport_open(port, pairing_path, &transport, message, capacity);
    if (!code) {
        struct RFCoreLocationHandle *location = NULL;
        int unavailable = fail(rf_core_location_connect_rsd(transport.adapter, transport.handshake, &location),
                               "系统路线服务尚未就绪", message, capacity);
        if (location) rf_core_location_free(location);
        *ready = unavailable == 0;
    }
    transport_close(&transport);
    return code;
}
int rf_setup_initialize(uint16_t port, const char *pairing_path, const char *restore_path,
                        char *message, size_t capacity) {
    if (!restore_path) return invalid("安装包缺少初始化文件", message, capacity);
    struct SetupTransport transport = {0};
    struct Cryptex1AssetsHandle *assets = NULL;
    struct InstalledCryptexC *installed = NULL;
    int code = transport_open(port, pairing_path, &transport, message, capacity);
    if (code) goto finished;
    code = fail(cryptexd_installed_ddi(transport.adapter, transport.handshake, &installed),
                "检查开发服务失败", message, capacity);
    if (code || installed) goto finished;
    code = fail(cryptex1_assets_load(restore_path, &assets), "初始化文件校验失败", message, capacity);
    if (code) goto finished;
    idevice_set_global_timeout(120);
    code = fail(cryptexd_install_ddi(transport.adapter, transport.handshake, assets, &installed),
                "本机开发服务初始化失败，请检查联网状态与开发者模式", message, capacity);
finished:
    idevice_set_global_timeout(10);
    if (installed) cryptexd_free_installed_cryptex(installed);
    if (assets) cryptex1_assets_free(assets);
    transport_close(&transport);
    return code;
}
