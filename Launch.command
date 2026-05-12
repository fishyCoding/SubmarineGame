#!/bin/bash

# move to the folder this script lives in
cd "$(dirname "$0")"

# compile everything
javac -cp ".:kryonet-2.21-all.jar" *.java
if [ $? -ne 0 ]; then
    echo "Compilation failed. Press enter to close."
    read
    exit 1
fi

# run the menu — Menu.main() calls Game.main() with the chosen args
java -cp ".:kryonet-2.21-all.jar" Menu
