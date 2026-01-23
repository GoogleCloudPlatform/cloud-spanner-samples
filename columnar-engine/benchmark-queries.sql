/*
 * Copyright 2026 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#n1
@{scan_method=no_columnar} SELECT WatchID FROM hits WHERE AdvEngineID = 22;
@{scan_method=columnar}SELECT WatchID FROM hits WHERE AdvEngineID = 22;

#n2
@{scan_method=no_columnar}SELECT COUNT(*) FROM hits WHERE AdvEngineID = 22;                                                                                                
@{scan_method=columnar}SELECT COUNT(*) FROM hits WHERE AdvEngineID = 22;

#n3
@{scan_method=no_columnar}SELECT COUNT(*) FROM hits;
@{scan_method=columnar}SELECT COUNT(*) FROM hits;

#n4
@{scan_method=no_columnar}SELECT COUNT(*) FROM hits WHERE AdvEngineID <> 0;
@{scan_method=columnar}SELECT COUNT(*) FROM hits WHERE AdvEngineID <> 0;

#n5
@{scan_method=no_columnar}SELECT AdvEngineID, COUNT(*), MIN(Age), MAX(Age) FROM hits GROUP BY AdvEngineID;
@{scan_method=columnar}SELECT AdvEngineID, COUNT(*), MIN(Age), MAX(Age) FROM hits GROUP BY AdvEngineID;

#n6
@{scan_method=no_columnar}SELECT IsMobile, COUNT(*) FROM hits GROUP BY IsMobile;
@{scan_method=columnar}SELECT IsMobile, COUNT(*) FROM hits GROUP BY IsMobile;

#n7
@{scan_method=no_columnar}SELECT IsLink, IsDownload, IsMobile, COUNT(*) FROM hits GROUP BY IsLink, IsDownload, IsMobile;
@{scan_method=columnar}SELECT IsLink, IsDownload, IsMobile, COUNT(*) FROM hits GROUP BY IsLink, IsDownload, IsMobile;

#n8
@{scan_method=no_columnar}SELECT COUNT(DISTINCT ClientIP) FROM hits;
@{scan_method=columnar}SELECT COUNT(DISTINCT ClientIP) FROM hits;

#n9
@{scan_method=no_columnar}SELECT IsDownload, COUNT(*) FROM hits WHERE ClientIP = 43648700 GROUP BY IsDownload;
@{scan_method=columnar}SELECT IsDownload, COUNT(*) FROM hits WHERE ClientIP = 43648700 GROUP BY IsDownload;

#n10
@{scan_method=no_columnar}SELECT COUNT(DISTINCT AdvEngineID) FROM hits;
@{scan_method=columnar}SELECT COUNT(DISTINCT AdvEngineID) FROM hits;

#n11
@{scan_method=no_columnar}SELECT MIN(EventDate), MAX(EventDate) FROM hits;
@{scan_method=columnar}SELECT MIN(EventDate), MAX(EventDate) FROM hits;

#n12
@{scan_method=no_columnar}SELECT UserID FROM hits WHERE UserID = 435090932899640449;
@{scan_method=columnar}SELECT UserID FROM hits WHERE UserID = 435090932899640449;
