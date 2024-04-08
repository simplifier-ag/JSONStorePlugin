#!/usr/bin/env bash

#
# Plugin installation script
#


# Parameter: installation target path (i.e. /opt/simplifier inside the simplifier container)
INSTALL_TARGET=$1


# get path of installation source files (location of setup script)
INSTALL_SRC="$( cd -- "$(dirname "$0")" >/dev/null 2>&1 ; pwd -P )"

# copy plugin jar and config files
cp ${INSTALL_SRC}/assets/* ${INSTALL_TARGET}/appserver/plugins




