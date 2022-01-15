libbluetooth_gnss
=================


About
-----

This is the engine part of the app, for the main app please visit: <https://github.com/ykasidit/bluetooth_gnss>

This android-studio project repo contains a few main android library 'modules' which allows Android phones to connect and read/parse GNSS (GPS/GLONASS/Galileo/BeiDou) position data from existing Bluetooth GNSS Receivers like the [EcoDroidGPS Bluetooth GPS Receiver](https://www.clearevo.com/ecodroidgps) - which is the main target device and developed by the same author of this project and also others similar 'nmea-like-messages over RFCOMM' devices like the HOLUX Bluetooth GPS devices, the Garmin GLO, etc.

The main modules are listed below:

- libecodroidbluetooth
  - Connects and manages IO operations over bluetooth to target devices as well as provide paired bluetooth device lists and Android related bluetooth utility functions.

- libecodroidgnss_parse
  - Parses nmea-like or other format data streamed from supported GNSS Receiver devices.
  - This module project uses the 'Java Marine API' - Full credit and thanks to its author 'Kimmo Tuukkanen' - please refer to its copyright and license info at: https://github.com/ktuukkan/marine-api

- libbluetooth_gnss_service
  - Android Service (forground) that uses both above and perform the main function of this project.


Special thanks
--------------

- Special thanks to 'Geoffrey Peck' from Australia for all his tests, observations and suggestions on Galileo support for the 'EcoDroidGPS GNSS Receiver' which led to this project, and also providing example sentence logs where RMC sentence lines got mixed with UBX messages - this is used in the unit-tests of this project to handle and still make use of lines that got binary messages mixed into the start of the same line.
- Thanks to Geoffrey Peck from Australia for his tests, observations and suggestions.
- Thanks to Peter Mousley from Australia for his expert advice, tests, code review and guidance.


Authors
-------

Kasidit Yusuf <ykasidit@gmail.com>


Copyright and License
---------------------

Copyright (C) 2019 Kasidit Yusuf <ykasidit@gmail.com>

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.

