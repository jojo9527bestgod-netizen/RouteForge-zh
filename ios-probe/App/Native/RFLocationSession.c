#include "RFLocationSession.h"
#include "idevice.h"
#include <arpa/inet.h>
#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

struct RFLocationSession {
    struct AdapterHandle *adapter;
    struct RsdHandshakeHandle *handshake;
    struct RFCoreLocationHandle *location;
};

static int failure(struct IdeviceFfiError *error, const char *stage,
                   char *message, size_t capacity) {
    if (!error) return 0;
    int code = error->code ? error->code : -1;
    /* Only stage and numeric code cross into UI/logs, never pairing contents. */
    if (message && capacity) snprintf(message, capacity, "%s（错误 %d）", stage, code);
    idevice_error_free(error);
    return code;
}

static int invalid(const char *text, char *message, size_t capacity) {
    if (message && capacity) snprintf(message, capacity, "%s", text);
    return -1;
}

void rf_session_close(RFLocationSession *session) {
    if (!session) return;
    if (session->location) rf_core_location_free(session->location);
    if (session->handshake) rsd_handshake_free(session->handshake);
    if (session->adapter) adapter_free(session->adapter);
    free(session);
}

int rf_pairing_validate(const char *path, char *message, size_t capacity) {
    if (!path) return invalid("请选择配对文件", message, capacity);
    struct RpPairingFileHandle *pairing = NULL;
    int result = failure(rp_pairing_file_read(path, &pairing), "配对文件不可用", message, capacity);
    if (pairing) rp_pairing_file_free(pairing);
    return result;
}

int rf_session_open(uint16_t port, const char *pairing_path,
                    RFLocationSession **out, char *message, size_t capacity) {
    if (!out || !pairing_path || !port) return invalid("连接配置不完整", message, capacity);
    *out = NULL;
    idevice_set_global_timeout(10);
    RFLocationSession *session = calloc(1, sizeof(*session));
    if (!session) return invalid("无法分配连接内存", message, capacity);
    struct RpPairingFileHandle *pairing = NULL;
    int code = failure(rp_pairing_file_read(pairing_path, &pairing), "读取配对文件失败", message, capacity);
    if (code) goto failed;
    struct sockaddr_in address;
    memset(&address, 0, sizeof(address));
#ifdef __APPLE__
    address.sin_len = sizeof(address);
#endif
    address.sin_family = AF_INET;
    address.sin_port = htons(port);
    inet_pton(AF_INET, "10.7.0.1", &address.sin_addr);
    code = failure(tunnel_create_rppairing(
        (const idevice_sockaddr *)&address, sizeof(address), "RouteForge iOS Probe",
        pairing, NULL, NULL, &session->adapter, &session->handshake),
        "本机通道连接失败，请检查 Wi-Fi、LocalDevVPN 和配对", message, capacity);
    rp_pairing_file_free(pairing);
    pairing = NULL;
    if (code) goto failed;
    code = failure(rf_core_location_connect_rsd(session->adapter, session->handshake, &session->location),
        "系统路线服务未就绪，请先检查本机连接与初始化", message, capacity);
    if (code) goto failed;
    *out = session;
    return 0;
failed:
    if (pairing) rp_pairing_file_free(pairing);
    rf_session_close(session);
    return code;
}

int rf_session_set(RFLocationSession *session, double latitude, double longitude,
                   char *message, size_t capacity) {
    if (!session || !session->location) return invalid("位置通道未连接", message, capacity);
    if (!isfinite(latitude) || !isfinite(longitude) || fabs(latitude) > 90 || fabs(longitude) > 180)
        return invalid("经纬度超出范围", message, capacity);
    return failure(rf_core_location_set(session->location, latitude, longitude),
                   "位置更新失败", message, capacity);
}

int rf_session_route(RFLocationSession *session, const RFWaypoint *points, size_t count,
                     double speed_mps, char *message, size_t capacity) {
    if (!session || !session->location) return invalid("位置通道未连接", message, capacity);
    return failure(rf_core_location_start(session->location, points, count, speed_mps),
                   "系统路线播放失败", message, capacity);
}

int rf_session_clear(RFLocationSession *session, char *message, size_t capacity) {
    if (!session || !session->location) return invalid("位置通道未连接", message, capacity);
    return failure(rf_core_location_clear(session->location), "清除模拟位置失败", message, capacity);
}
