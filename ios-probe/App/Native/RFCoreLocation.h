#ifndef RF_CORE_LOCATION_H
#define RF_CORE_LOCATION_H
#include <stddef.h>
struct AdapterHandle;
struct RsdHandshakeHandle;
struct IdeviceFfiError;
struct RFCoreLocationHandle;
/* Layout shared with the extension compiled inside idevice-ffi. */
typedef struct RFWaypoint { double latitude; double longitude; } RFWaypoint;
struct IdeviceFfiError *rf_core_location_connect_rsd(struct AdapterHandle *, struct RsdHandshakeHandle *, struct RFCoreLocationHandle **);
struct IdeviceFfiError *rf_core_location_set(struct RFCoreLocationHandle *, double, double);
struct IdeviceFfiError *rf_core_location_start(struct RFCoreLocationHandle *, const RFWaypoint *, size_t, double);
struct IdeviceFfiError *rf_core_location_clear(struct RFCoreLocationHandle *);
void rf_core_location_free(struct RFCoreLocationHandle *);
#endif
