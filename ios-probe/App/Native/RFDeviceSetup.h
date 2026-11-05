#ifndef RF_DEVICE_SETUP_H
#define RF_DEVICE_SETUP_H
#include <stddef.h>
#include <stdint.h>

typedef struct RFPairingHost RFPairingHost;
typedef void (*RFPairingPinCallback)(const char *pin, void *context);
int rf_pair_host_create(RFPairingHost **out, char *message, size_t capacity);
const char *rf_pair_host_service_name(RFPairingHost *host);
uint16_t rf_pair_host_port(RFPairingHost *host);
const uint8_t *rf_pair_host_txt(RFPairingHost *host, size_t *length);
int rf_pair_host_accept(RFPairingHost *host, const char *output_path,
                       RFPairingPinCallback callback, void *context,
                       char *message, size_t capacity);
void rf_pair_host_cancel(RFPairingHost *host);
/* Release only after accept returns. Cancel is safe while accept is in flight. */
void rf_pair_host_free(RFPairingHost *host);
int rf_setup_probe(uint16_t port, const char *pairing_path, int *ready,
                   char *message, size_t capacity);
int rf_setup_initialize(uint16_t port, const char *pairing_path,
                        const char *restore_path, char *message, size_t capacity);
#endif
