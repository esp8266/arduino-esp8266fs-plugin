# Arduino ESP8266 filesystem uploader [![Build Status](https://travis-ci.org/esp8266/arduino-esp8266fs-plugin.svg?branch=master)](https://travis-ci.org/esp8266/arduino-esp8266fs-plugin)

Arduino plugin which packs sketch data folder into SPIFFS filesystem image,
and uploads the image to ESP8266 flash memory.
<br>
Tested with the following Arduino IDE versions: 1.6.5-r2,
1.6.6



## Installation
- Make sure you use one of the supported versions of Arduino IDE and have ESP8266 core installed.
- Download the tool archive from [releases page](https://github.com/esp8266/arduino-esp8266fs-plugin/releases/latest).
- In your Arduino sketchbook directory, create tools directory if it doesn't exist yet.
- Unpack the tool into tools directory (the path will look like `<home_dir>/Arduino/tools/ESP8266FS/tool/esp8266fs.jar)`.
- Restart Arduino IDE. 

On the OS X create the tools directory in ~/Documents/Arduino/ and unpack the files there

## Usage
- Open a sketch (or create a new one and save it).
- Go to sketch directory (choose Sketch > Show Sketch Folder).
- Create a directory named `data` and any files you want in the file system there.
- Make sure you have selected a board, port, and closed Serial Monitor.
- Select *Tools > ESP8266 Sketch Data Upload* menu item. This should start uploading the files into ESP8266 flash file system.
  When done, IDE status bar will display SPIFFS Image Uploaded message. Might take a few minutes for large file system sizes.

## Screenshot

![Screenshot](screenshot.png)

## Credits and license

- Copyright (c) 2015 Hristo Gochkov (ficeto at ficeto dot com)
- Licensed under GPL v2 ([text](LICENSE))
- Maintained by Ivan Grokhotkov (ivan at esp8266 dot com)

## Issues and suggestions

File issues here on github, or ask your questions on the [esp8266.com forum](http://esp8266.com/arduino).
