#!/usr/bin/env bash

if [[ -z "$INSTALLDIR" ]]; then
    if [[ -z "$TAG" ]]; then
        TAG=1.6.12
    fi
    INSTALLDIR="$HOME/Arduino-$TAG/build/linux/work"
fi
if [[ -L "$INSTALLDIR" ]]; then
    INSTALLDIR=$INSTALLDIR/
fi
echo "INSTALLDIR: $INSTALLDIR"

pde_path=`find ${INSTALLDIR} -name pde.jar`
core_path=`find ${INSTALLDIR} -name arduino-core.jar`
lib_path=`find ${INSTALLDIR} -name commons-codec-1.7.jar`
if [[ -z "$core_path" || -z "$pde_path" ]]; then
    echo "Some java libraries have not been built yet (did you run ant build?)"
    exit 1
fi
echo "pde_path: $pde_path"
echo "core_path: $core_path"
echo "lib_path: $lib_path"

set -e

mkdir -p bin
javac -target 1.8 -cp "$pde_path:$core_path:$lib_path" \
      -d bin src/ESP8266FS.java

pushd bin
mkdir -p $INSTALLDIR/tools
rm -rf $INSTALLDIR/tools/ESP8266FS
mkdir -p $INSTALLDIR/tools/ESP8266FS/tool
zip -r $INSTALLDIR/tools/ESP8266FS/tool/esp8266fs.jar *
popd

dist=$PWD/dist
rev=$(git describe --tags)
mkdir -p $dist
pushd $INSTALLDIR/tools
zip -r $dist/ESP8266FS-$rev.zip ESP8266FS/
popd
