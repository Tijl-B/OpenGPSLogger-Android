# openGPSLogger

## Releases

### 0.10.0
[apk](release/opengpslogger-0-10-0.apk)

Status: proof of concept. Bugs and bad UI/UX are expected. Core functionality works.

#### Features
- Allow importing json from local (phone) export of Google Maps Timeline data (Timeline.json)

### 0.9.0
[apk](release/opengpslogger-0-9-0.apk)

Status: proof of concept. Bugs and bad UI/UX are expected. Core functionality works.

#### Features
- Allow importing json from local (phone) export of Google Maps Timeline data (location-history.json)

### 0.8.0
[apk](release/opengpslogger-0-8-0.apk)

Status: proof of concept. Bugs and bad UI/UX are expected. Core functionality works.

#### Features
- Add minimum angle filter to filter out outliers

### 0.7.2
Status: proof of concept. Bugs and bad UI/UX are expected. Core functionality works.

#### Fixes
- Fix notification counter being stuck on 0

### 0.7.1
Status: proof of concept. Bugs and bad UI/UX are expected. Core functionality works.

#### Fixes
- Improve back filling neighbor distance and angle to reduce database usage

### 0.7.0
Status: proof of concept. Bugs and bad UI/UX are expected. Core functionality works.

#### Features
- Store angle and distance to neighbors for future outlier detection

### 0.6.0
[apk](release/opengpslogger-0-6-0.apk)

Status: proof of concept. Bugs and bad UI/UX are expected. Core functionality works.

#### Features
- Add visualisation settings
  - specify point size
  - toggle lines (new)
  - specify line size
  - specify line disconnection by time

### 0.5.0
[apk](release/opengpslogger-0-5-0.apk)

Status: proof of concept. Bugs and bad UI/UX are expected. Core functionality works.

#### Features
- Add minimum accuracy filter

### 0.4.1
Status: proof of concept. Bugs and bad UI/UX are expected. Core functionality works.

#### Fixes
- Fix notification sometimes showing 0 points tracked instead of actual amount

### 0.4.0
[apk](release/opengpslogger-0-4-0.apk)

Status: proof of concept. Bugs and bad UI/UX are expected. Core functionality works.

#### Features
- Allow deleting user provided bounding boxes

### 0.3.0
[apk](release/opengpslogger-0-3-0.apk)

Status: proof of concept. Bugs and bad UI/UX are expected. Core functionality works.

#### Features
- When clicking on the preview image, a popup opens allowing to zoom in

### 0.2.0
[apk](release/opengpslogger-0-2-0.apk)

Status: proof of concept. Bugs and bad UI/UX are expected. Core functionality works.

#### Features
- Add tracking settings with highest, high, medium, low and passive presets

#### Fixes
- When swiping away notification, tracking is terminated
- Tracking status is stored (correctly show start / stop tracking)

### 0.1.0

Status: proof of concept. Bugs and bad UI/UX are expected. Core functionality works.

#### Features
- Add about page

### 0.0.1
[apk](release/opengpslogger-0-0-1.apk)


Status: proof of concept. Bugs and bad UI/UX are expected. Core functionality works.

#### Features
- Track GPS in app
- Import `.gpx` files and `Records.json` from Google Takeout Location History (via sharing file from other app)
- Visualise points on OpenStreetMap background (copyright disclaimer: https://www.openstreetmap.org/copyright)
- Filter visualisation based on bounding box, time range and / or datasource
- Save bounding boxes
- Save visualisation as image
- View and backup database with point data (sqlite)

#### Known issues
- Database restore is not possible
- Tracking may stop working after some time, despite the notification still being present
- No app icon
- Deleting saved bounding boxes is not possible
- Importing gpx / json file directly via app is not possible
- After importing gpx / json file, app must be manually closed and reopened
- Setting button doesn't do anything

#### Images
<img src="images/0.0.1/nyc.png" width="40%" height="40%">
<img src="images/0.0.1/main_page.jpg" width="40%" height="40%">
<img src="images/0.0.1/bounding_box_selection.jpg" width="40%" height="40%">
<img src="images/0.0.1/database_page.jpg" width="40%" height="40%">
