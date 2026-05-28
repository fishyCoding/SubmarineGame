Sebastian Balestri

Submarine Game 

This project is a 2d submarine warfare game that I have created. It is a multiplayer game that runs on LAN

Executing the project:

There are two main codes to run. 

First, to run the game engine (world editor), run java Main . This gives the user the ability to edit their world.
The data for the rocks is stored in the file sprites.txt and the data for the bottom floor is stored in seafloor.txt. These two files must exist in the directory for the game to run correctly. 

To run the actual game, run java -cp ".:kryonet-2.21-all.jar" Menu . This is because the code needs to exectue with the kryonet jar files, which contains the code for the server/client communication

Data types: 

Main class hierarchy

Sprites (Stores x and y and contains a draw function)
|                                                |
Rocks                                        Character (Stores rotation, velocity)
(Load vertecies from txt file and draw)         |                |
                                            Submarine         Torpedo

Rocks have two draw functions, main draw and radar draw. The main draw draws the normal rock form. The radar draw draws the rock with a green outline, which is used in the radar view. 

Data files:

seafloor.txt: stores seafloor points
sprites.txt: stores rock vertecies

Bugs:
The collision isnt great, it decides if the center of the object is inside the bounds of the rock, but this doesnt always work

Very relient on client side, if a hacker were to manipulate the client side, it would be very easy to cheat

Help:
https://github.com/EsotericSoftware/kryonet/blob/master/README.md(Instructions for the kryonet)

https://en.wikipedia.org/wiki/Point_in_polygon
For collision algorithm


The largest problem I encountered was setting up the multiplayer. Eventually, I settled on handling all of the collision detection and explosion logic client side because it was too hard to do it on the server

