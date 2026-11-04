import CoreLocation
import MapKit
import RouteCore
import SwiftUI

@MainActor
struct RouteMapView: UIViewRepresentable {
    let waypoints: [Waypoint]
    let route: RoutePath?
    let revision: Int
    let livePoint: GeoPoint?
    let editable: Bool
    let satellite: Bool
    let fitRequest: Int
    let locationRequest: Int
    let focusRequest: Int
    let focusedPoint: GeoPoint?
    let onAdd: (GeoPoint) -> Void
    let onMove: (UUID, GeoPoint) -> Void
    let onRegion: (MKCoordinateRegion) -> Void
    let onLocationError: (String) -> Void

    func makeCoordinator() -> Coordinator { Coordinator(parent: self) }
    func makeUIView(context: Context) -> MKMapView {
        let map = MKMapView()
        map.delegate = context.coordinator
        map.showsScale = true
        map.showsCompass = true
        map.isPitchEnabled = false
        map.layoutMargins = UIEdgeInsets(top: 70, left: 12, bottom: 8, right: 12)
        map.setRegion(MKCoordinateRegion(center: CLLocationCoordinate2D(latitude: 48.85837, longitude: 2.29448), span: MKCoordinateSpan(latitudeDelta: 0.035, longitudeDelta: 0.035)), animated: false)
        let press = UILongPressGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.longPress(_:)))
        press.minimumPressDuration = 0.45
        press.delegate = context.coordinator
        press.cancelsTouchesInView = false
        map.addGestureRecognizer(press)
        context.coordinator.map = map
        context.coordinator.requestLocationPermission()
        return map
    }
    func updateUIView(_ map: MKMapView, context: Context) {
        let coordinator = context.coordinator
        coordinator.parent = self
        map.mapType = satellite ? .hybrid : .standard
        let authorized = coordinator.authorized
        map.showsUserLocation = authorized && livePoint == nil
        if coordinator.lastWaypoints != waypoints {
            map.removeAnnotations(map.annotations.compactMap { $0 as? WaypointAnnotation })
            for (index, waypoint) in waypoints.enumerated() {
                map.addAnnotation(WaypointAnnotation(waypoint, index: index, count: waypoints.count))
            }
            coordinator.lastWaypoints = waypoints
        }
        for annotation in map.annotations {
            if let pin = annotation as? WaypointAnnotation,
               let view = map.view(for: pin) as? MKMarkerAnnotationView { view.isDraggable = editable }
        }
        if coordinator.lastRevision != revision {
            map.removeOverlays(map.overlays)
            if let route {
                var coordinates = route.points.map(\.coordinate)
                map.addOverlay(MKPolyline(coordinates: &coordinates, count: coordinates.count))
            }
            coordinator.lastRevision = revision
        }
        if let livePoint {
            if let pin = coordinator.simulation {
                pin.coordinate = livePoint.coordinate
            } else {
                let pin = SimulationAnnotation(coordinate: livePoint.coordinate)
                coordinator.simulation = pin; map.addAnnotation(pin)
            }
        } else if let pin = coordinator.simulation {
            map.removeAnnotation(pin); coordinator.simulation = nil
        }
        if coordinator.lastFit != fitRequest {
            coordinator.lastFit = fitRequest
            coordinator.fit()
        }
        if coordinator.lastFocus != focusRequest {
            coordinator.lastFocus = focusRequest
            if let point = focusedPoint {
                coordinator.centeredOnUser = true
                map.setRegion(MKCoordinateRegion(center: point.coordinate, span: MKCoordinateSpan(latitudeDelta: 0.018, longitudeDelta: 0.018)), animated: true)
            }
        }
        if coordinator.lastLocate != locationRequest {
            coordinator.lastLocate = locationRequest
            coordinator.locate()
        }
    }

    @MainActor
    final class Coordinator: NSObject, MKMapViewDelegate, CLLocationManagerDelegate, UIGestureRecognizerDelegate {
        var parent: RouteMapView
        weak var map: MKMapView?
        let location = CLLocationManager()
        var lastWaypoints: [Waypoint] = []
        var lastRevision = -1
        var lastFit = 0
        var lastLocate = 0
        var lastFocus = 0
        var simulation: SimulationAnnotation?
        var centeredOnUser = false
        var authorized: Bool { location.authorizationStatus == .authorizedAlways || location.authorizationStatus == .authorizedWhenInUse }
        init(parent: RouteMapView) { self.parent = parent; super.init(); location.delegate = self }
        func requestLocationPermission() {
            if location.authorizationStatus == .notDetermined { location.requestWhenInUseAuthorization() }
            map?.showsUserLocation = authorized && parent.livePoint == nil
        }
        func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
            map?.showsUserLocation = authorized && parent.livePoint == nil
        }
        func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldReceive touch: UITouch) -> Bool {
            var view = touch.view
            while let current = view {
                if current is MKAnnotationView { return false }
                view = current.superview
            }
            return parent.editable
        }
        @objc func longPress(_ gesture: UILongPressGestureRecognizer) {
            guard gesture.state == .began, parent.editable, let map,
                  let point = try? GeoPoint(
                    latitude: map.convert(gesture.location(in: map), toCoordinateFrom: map).latitude,
                    longitude: map.convert(gesture.location(in: map), toCoordinateFrom: map).longitude
                  ) else { return }
            parent.onAdd(point)
        }
        func fit() {
            guard let map else { return }
            var bounds = MKMapRect.null
            let coordinates = parent.route?.points.map(\.coordinate) ?? parent.waypoints.map { $0.point.coordinate }
            for coordinate in coordinates {
                let point = MKMapPoint(coordinate)
                bounds = bounds.union(MKMapRect(x: point.x, y: point.y, width: 1, height: 1))
            }
            guard !bounds.isNull else { return }
            centeredOnUser = true
            map.setVisibleMapRect(bounds, edgePadding: UIEdgeInsets(top: 100, left: 55, bottom: 65, right: 55), animated: true)
        }
        func locate() {
            guard let map else { return }
            if let point = parent.livePoint { map.setCenter(point.coordinate, animated: true); return }
            guard authorized else {
                requestLocationPermission()
                parent.onLocationError("请在系统设置中允许本 App 使用定位；也可继续搜索或长按地图选点。")
                return
            }
            if CLLocationCoordinate2DIsValid(map.userLocation.coordinate), map.userLocation.location != nil {
                map.setCenter(map.userLocation.coordinate, animated: true)
            } else { map.setUserTrackingMode(.follow, animated: true) }
        }
        func mapView(_ mapView: MKMapView, regionDidChangeAnimated animated: Bool) { parent.onRegion(mapView.region) }
        func mapView(_ mapView: MKMapView, didUpdate userLocation: MKUserLocation) {
            guard !centeredOnUser, parent.waypoints.isEmpty, parent.route == nil,
                  let location = userLocation.location, location.horizontalAccuracy >= 0 else { return }
            centeredOnUser = true
            mapView.setRegion(MKCoordinateRegion(center: location.coordinate, span: MKCoordinateSpan(latitudeDelta: 0.018, longitudeDelta: 0.018)), animated: true)
        }
        func mapView(_ mapView: MKMapView, rendererFor overlay: MKOverlay) -> MKOverlayRenderer {
            guard let line = overlay as? MKPolyline else { return MKOverlayRenderer(overlay: overlay) }
            let renderer = MKPolylineRenderer(polyline: line)
            renderer.strokeColor = .systemBlue; renderer.lineWidth = 5
            renderer.lineJoin = .round; renderer.lineCap = .round
            return renderer
        }
        func mapView(_ mapView: MKMapView, viewFor annotation: MKAnnotation) -> MKAnnotationView? {
            if annotation is MKUserLocation { return nil }
            let identifier = annotation is SimulationAnnotation ? "simulation" : "waypoint"
            let view = (mapView.dequeueReusableAnnotationView(withIdentifier: identifier) as? MKMarkerAnnotationView)
                ?? MKMarkerAnnotationView(annotation: annotation, reuseIdentifier: identifier)
            view.annotation = annotation; view.displayPriority = .required; view.canShowCallout = true
            if let pin = annotation as? WaypointAnnotation {
                view.markerTintColor = pin.index == 0 ? .systemGreen : pin.index == pin.count - 1 ? .systemRed : .systemBlue
                view.glyphText = pin.index == 0 ? "起" : pin.index == pin.count - 1 ? "终" : String(pin.index)
                view.glyphImage = nil; view.isDraggable = parent.editable
            } else {
                view.markerTintColor = .systemBlue; view.glyphText = nil
                view.glyphImage = UIImage(systemName: "location.north.fill"); view.isDraggable = false
            }
            return view
        }
        func mapView(_ mapView: MKMapView, annotationView view: MKAnnotationView,
                     didChange newState: MKAnnotationView.DragState, fromOldState oldState: MKAnnotationView.DragState) {
            guard newState == .ending, let pin = view.annotation as? WaypointAnnotation,
                  let point = try? GeoPoint(latitude: pin.coordinate.latitude, longitude: pin.coordinate.longitude) else { return }
            view.dragState = .none
            parent.onMove(pin.id, point)
        }
    }
}

final class WaypointAnnotation: NSObject, MKAnnotation {
    let id: UUID
    let index: Int
    let count: Int
    @objc dynamic var coordinate: CLLocationCoordinate2D
    var title: String? { index == 0 ? "起点" : index == count - 1 ? "终点" : "途经点 \(index)" }
    let subtitle: String?
    init(_ waypoint: Waypoint, index: Int, count: Int) {
        id = waypoint.id; self.index = index; self.count = count
        coordinate = waypoint.point.coordinate; subtitle = waypoint.name
        super.init()
    }
}

final class SimulationAnnotation: NSObject, MKAnnotation {
    @objc dynamic var coordinate: CLLocationCoordinate2D
    var title: String? { "模拟位置" }
    init(coordinate: CLLocationCoordinate2D) { self.coordinate = coordinate; super.init() }
}
