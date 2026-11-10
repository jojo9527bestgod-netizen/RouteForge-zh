//! RouteForge additions to the pinned MIT idevice FFI crate.
//! Compiled inside that crate so its Rust handles and runtime have one owner.
use std::ptr::null_mut;

use idevice::core_device::CoreDeviceServiceClient;
use idevice::{IdeviceError, ReadWrite, RsdService};
use plist::{Dictionary, Value};

use crate::core_device_proxy::AdapterHandle;
use crate::rsd::RsdHandshakeHandle;
use crate::{IdeviceFfiError, ffi_err, run_global_timeout, run_sync_local};

const FEATURE: &str = "com.apple.coredevice.feature.simulatelocation";
const CLEAR: &str = "com.apple.coredevice.action.clearsimulatedlocation";
const SET: &str = "com.apple.coredevice.action.setsimulatedlocation";
const START: &str = "com.apple.coredevice.action.startlocationsimulation";

pub struct RFCoreLocationHandle(CoreDeviceServiceClient<Box<dyn ReadWrite>>);

impl RsdService for RFCoreLocationHandle {
    fn rsd_service_name() -> std::borrow::Cow<'static, str> {
        "com.apple.coredevice.locationservice".into()
    }

    async fn from_stream(stream: Box<dyn ReadWrite>) -> Result<Self, IdeviceError> {
        Ok(Self(CoreDeviceServiceClient::new(stream).await?))
    }
}

#[repr(C)]
#[derive(Clone, Copy)]
pub struct RFWaypoint {
    pub latitude: f64,
    pub longitude: f64,
}

impl RFWaypoint {
    fn valid(&self) -> bool {
        self.latitude.is_finite() && self.longitude.is_finite()
            && self.latitude.abs() <= 90.0 && self.longitude.abs() <= 180.0
    }

    fn dictionary(self) -> Dictionary {
        Dictionary::from_iter([
            ("latitude", Value::Real(self.latitude)),
            ("longitude", Value::Real(self.longitude)),
        ])
    }
}

/// # Safety
/// All handles must belong to this build of idevice-ffi. Output must be writable.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn rf_core_location_connect_rsd(
    adapter: *mut AdapterHandle,
    handshake: *mut RsdHandshakeHandle,
    output: *mut *mut RFCoreLocationHandle,
) -> *mut IdeviceFfiError {
    if adapter.is_null() || handshake.is_null() || output.is_null() {
        return ffi_err!(IdeviceError::FfiInvalidArg);
    }
    unsafe { *output = null_mut() };
    let result = run_sync_local(async {
        run_global_timeout(|| async {
            RFCoreLocationHandle::connect_rsd(
                unsafe { &mut (*adapter).0 }, unsafe { &mut (*handshake).0 },
            ).await
        }).await
    });
    match result {
        Ok(client) => { unsafe { *output = Box::into_raw(Box::new(client)) }; null_mut() }
        Err(error) => ffi_err!(error),
    }
}

fn invoke(handle: &mut RFCoreLocationHandle, input: Dictionary, action: &str) -> *mut IdeviceFfiError {
    match run_sync_local(async {
        run_global_timeout(|| handle.0.invoke_with_plist_action(FEATURE, input, action)).await
    }) {
        Ok(_) => null_mut(),
        Err(error) => ffi_err!(error),
    }
}

fn route_input(points: &[RFWaypoint], speed_mps: f64) -> Result<Dictionary, IdeviceError> {
    if !(2..=50_000).contains(&points.len()) || !speed_mps.is_finite()
        || speed_mps <= 0.0 || speed_mps > 150.0 / 3.6 || points.iter().any(|point| !point.valid()) {
        return Err(IdeviceError::FfiInvalidArg);
    }
    let interval = Dictionary::from_iter([("_0", Value::Real(1.0))]);
    let pacing = Dictionary::from_iter([("interval", Value::Dictionary(interval))]);
    Ok(Dictionary::from_iter([
        ("waypoints", Value::Array(points.iter().map(|point| Value::Dictionary(point.dictionary())).collect())),
        ("speed", Value::Real(speed_mps)),
        ("pacing", Value::Dictionary(pacing)),
    ]))
}

/// # Safety
/// Handle must be valid and exclusively owned for this synchronous call.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn rf_core_location_clear(handle: *mut RFCoreLocationHandle) -> *mut IdeviceFfiError {
    if handle.is_null() { return ffi_err!(IdeviceError::FfiInvalidArg); }
    invoke(unsafe { &mut *handle }, Dictionary::new(), CLEAR)
}

/// Stops any active route before holding a fixed position.
/// # Safety
/// Handle must be valid and exclusively owned for this synchronous call.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn rf_core_location_set(
    handle: *mut RFCoreLocationHandle, latitude: f64, longitude: f64,
) -> *mut IdeviceFfiError {
    let point = RFWaypoint { latitude, longitude };
    if handle.is_null() || !point.valid() { return ffi_err!(IdeviceError::FfiInvalidArg); }
    let client = unsafe { &mut *handle };
    let error = invoke(client, Dictionary::new(), CLEAR);
    if !error.is_null() { return error; }
    invoke(client, point.dictionary(), SET)
}

/// # Safety
/// Points must be a readable array of count entries and handle exclusively owned.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn rf_core_location_start(
    handle: *mut RFCoreLocationHandle, points: *const RFWaypoint, count: usize, speed_mps: f64,
) -> *mut IdeviceFfiError {
    if handle.is_null() || points.is_null() || !(2..=50_000).contains(&count)
        || !speed_mps.is_finite() || speed_mps <= 0.0 || speed_mps > 150.0 / 3.6 {
        return ffi_err!(IdeviceError::FfiInvalidArg);
    }
    let points = unsafe { std::slice::from_raw_parts(points, count) };
    match route_input(points, speed_mps) {
        Ok(input) => invoke(unsafe { &mut *handle }, input, START),
        Err(error) => ffi_err!(error),
    }
}

/// # Safety
/// Handle must come from connect_rsd and be freed at most once, or be null.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn rf_core_location_free(handle: *mut RFCoreLocationHandle) {
    if !handle.is_null() { drop(unsafe { Box::from_raw(handle) }); }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn native_payload_uses_metres_per_second_and_codable_interval() {
        let points = [RFWaypoint { latitude: 31.0, longitude: 121.0 },
                      RFWaypoint { latitude: 31.001, longitude: 121.001 }];
        let input = route_input(&points, 25.0 / 3.6).unwrap();
        assert_eq!(input["speed"].as_real(), Some(25.0 / 3.6));
        let pacing = input["pacing"].as_dictionary().unwrap();
        assert_eq!(pacing["interval"].as_dictionary().unwrap()["_0"].as_real(), Some(1.0));
        let waypoints = input["waypoints"].as_array().unwrap();
        assert_eq!(waypoints.len(), 2);
        assert_eq!(waypoints[1].as_dictionary().unwrap()["longitude"].as_real(), Some(121.001));
    }

    #[test]
    fn malformed_route_never_becomes_a_device_request() {
        let point = RFWaypoint { latitude: 31.0, longitude: 121.0 };
        assert!(route_input(&[point], 5.0).is_err());
        assert!(route_input(&[point; 50_001], 5.0).is_err());
        for speed in [0.0, -1.0, f64::NAN, f64::INFINITY, 150.1 / 3.6] {
            assert!(route_input(&[point; 2], speed).is_err());
        }
        for invalid in [RFWaypoint { latitude: 91.0, longitude: 0.0 },
                        RFWaypoint { latitude: 0.0, longitude: f64::NAN }] {
            assert!(route_input(&[point, invalid], 5.0).is_err());
        }
    }
}
