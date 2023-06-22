# SteamInspector
`SteamInspector` is a tool to take look at all the information about your Steam client and your installed games.  
It gathers this information from the installation folder of your steam client and especially its subfolders `appcache`, `steamapps` and `userdata` and, if you have one or more separate game libraries configured, also from there. You can see all screenshots, achievements, infos about trading cards and other game infos and also images the Steam client uses for its GUI and many more.

It includes a subtool named [`SteamScreenshotsCleanUp`](/src/net/schwarzbaer/java/tools/steaminspector/SteamScreenshotsCleanUp.java), which is used to show and delete screenshots of games installed in steam.
If you have configured in the Steam client an extra screenshot folder, where Steam can store an uncompressed copy of each screenshot, then `SteamScreenshotsCleanUp` can show and delete screenshots there too. The screenshots will be listed separated by game and it's shown, if there exists also an uncompressed version of a screenshot.

### Usage
You can download a release [here](https://github.com/Hendrik2319/SteamInspector/releases).  
You will need a JAVA 17 VM.

### Development
`SteamInspector` is configured as a Eclipse project.  
It depends on following libraries:
* [`JavaLib_JSON_Parser`](https://github.com/Hendrik2319/JavaLib_JSON_Parser)
* [`JavaLib_Common_Essentials`](https://github.com/Hendrik2319/JavaLib_Common_Essentials)
* [`JavaLib_Common_Dialogs`](https://github.com/Hendrik2319/JavaLib_Common_Dialogs)

These libraries are imported as "project imports" in Eclipse. 
If you want to develop for your own and
* you use Eclipse as IDE,
	* then you should clone the projects above too and add them to the same workspace as the `SteamInspector` project.
* you use another IDE (e.q. VS Code)
	* then you should clone the said projects, build JAR files of them and add the JAR files as libraries.

### Screenshots
`SteamInspector`  
<img alt="Screenshot 1 : SteamInspector" title="Screenshot 1 : SteamInspector" width="300" src="/github/Screenshot1 - SteamInspector.png" />  

`SteamScreenshotsCleanUp`  
<img alt="Screenshot 2 : SteamScreenshotsCleanUp" title="Screenshot 2 : SteamScreenshotsCleanUp"  width="300" src="/github/Screenshot2 - SteamScreenshotsCleanUp.png" />  
