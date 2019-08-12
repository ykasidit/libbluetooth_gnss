libbluetooth_gnss
=================


About
-----

This android-studio project repo contains a few main android library 'modules' used by the 'Bluetooth GNSS' App which allows Android phones to connect and read GNSS (GPS/GLONASS/Galileo/BeiDou) position data from existing Bluetooth GNSS Receivers like the [EcoDroidGPS Bluetooth GPS Receiver](https://www.clearevo.com/ecodroidgps) - which is the main target device and developed by the same author of this project, HOLUX Bluetooth GPS devices, the Garmin GLO, etc.

The main modules are listed below:

- libecodroidbluetooth
  - Connects and manages IO operations over bluetooth to target devices as well as provide paired bluetooth device lists and Android related bluetooth utility functions.

- libecodroidgnss_parse
  - Parses nmea-like or other format data streamed from supported GNSS Receiver devices.
  - This module project uses the 'Java Marine API' - Full credit and thanks to its author 'Kimmo Tuukkanen' - please refer to its copyright and license info at: https://github.com/ktuukkan/marine-api


Copyright and License
---------------------

Copyright (C) 2019  Kasidit Yusuf <ykasidit@gmail.com>

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


Authors
-------

Kasidit Yusuf <ykasidit@gmail.com>


Special thanks
--------------

- Special thanks to 'Geoffrey Peck' from Australia for providing example sentence logs where RMC sentence lines got mixed with UBX messages - this is used in the unit-tests of this project to handle and still make use of lines that got binary messages mixed into the start of the same line.