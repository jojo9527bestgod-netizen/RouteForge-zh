#ifndef RF_LOCATION_SESSION_H
#define RF_LOCATION_SESSION_H
#include <stddef.h>
#include <stdint.h>
#include "RFCoreLocation.h"

typedef struct RFLocationSession RFLocationSession;
int rf_pairing_validate(const char *path, char *message, size_t capacity);
int rf_session_open(uint16_t port, const char *pairing_path,
                    RFLocationSession **out, char *message, size_t capacity);
int rf_session_set(RFLocationSession *session, double latitude, double longitude,
                   char *message, size_t capacity);
int rf_session_route(RFLocationSession *session, const RFWaypoint *points, size_t count,
                     double speed_mps, char *message, size_t capacity);
int rf_session_clear(RFLocationSession *session, char *message, size_t capacity);
void rf_session_close(RFLocationSession *session);
#endif
