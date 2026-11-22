VW_AA_ClusterNav 0.2 - Android Auto turn data on the cluster and HUD
====================================================================
VW Touareg CR, firmware MH2p_ER_VWG36_P2869 only. Experimental.

New in 0.2
 - records what the cluster service sees as navigation data, from the factory
   navigation as well as from this plugin (to find out why the HUD draws no arrow)
 - street line: "towards X" becomes "X"; texts that are instructions and not
   streets ("Turn right", "Head east") are left out
 - no more brief flicker to "continue" before each new instruction

Install (replaces 0.1, same steps)
 1. Format the card FAT32, unzip so Data, Meta, Mods and Logs are in the ROOT.
 2. Engine running, phone unplugged, insert, wait for the prompt to remove.
 3. Logs/VW_AA_ClusterNav.log must end with "done: installed".

Recording drive (card OUT)
 A. Factory navigation: phone unplugged. Enter a nearby destination in the
    car's own navigation, start guidance, drive two or three turns while the
    HUD shows its arrows. Then stop the factory guidance.
 B. Android Auto: connect the phone, start Google Maps guidance, drive two
    or three turns.
 Then park, wait half a minute, unplug the phone, insert the card and let it
 run. It copies the log to Logs/vwaa_unit. Share the Logs folder.

Switch off without uninstalling: USB stick with an empty vwaa_off.txt.
Uninstall: empty file Mods/VW_AA_ClusterNav/uninstall.txt, run the card again.
Other markers (empty files on a USB stick): vwaa_nohide, vwaa_no838,
vwaa_nosnoop, vwaa_reset.

Logs/_ModKit_Update.log contains "error: servicemgrmibhigh.sh not found".
That is intended (keeps the unit's existing startup wrapper untouched).

Source and credits are in the Source folder. GPL-3.0; personal, non-commercial use.
