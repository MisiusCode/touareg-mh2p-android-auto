VW_AA_ClusterNav 0.5 - Android Auto turn data on the cluster and HUD
====================================================================
VW Touareg CR, firmware MH2p_ER_VWG36_P2869 only. Experimental.

New in 0.5 (automatic tries - low odds, but the log will be conclusive)
 Recorded with 0.4: with a factory route the HUD picture service reports
 "visible" and draws the arrow; with Android Auto guidance it stays invisible
 and ignores what the plugin sends. 0.5 tries the remaining switches of that
 service by itself while Android Auto guidance runs, one after the other:
   1 plain element (30 s)      4 view off and on (20 s)
   2 list form (20 s)          5 five other view styles (10 s each)
   3 declare visible (20 s)    6 arrow mode for the cluster channel (20 s)
 then a 60 s pause and rounds 2 and 3 (without try 6). For every try the log
 says whether the service reacted. If the HUD view turns visible, the plugin
 stops at that try and keeps it.

Install (replaces 0.4, same steps)
 1. Format the card FAT32, unzip so Data, Meta, Mods and Logs are in the ROOT.
 2. Engine running, phone unplugged, insert, wait for the prompt to remove.
 3. Logs/VW_AA_ClusterNav.log must end with "done: installed".

Test drive (card OUT)
 Android Auto with Google Maps, guidance running for at least 10 minutes
 without a break (a longer route; the tries need about 9 minutes). Glance at
 the HUD and cluster now and then: an arrow or anything unusual? Then park,
 wait half a minute, unplug the phone, run the card again, share the Logs.

 Tries 5 and 6 change how the factory side is set up (view style, guidance
 display mode). The style is put back by the plugin; both are set again by
 the unit at its next start. If something looks different afterwards with the
 factory navigation, let the car go to sleep once (locked, about 10 minutes).

 Markers on a USB stick: vwaa_noprobe.txt = plain element only,
 vwaa_nokomo.txt = stop the new part (street and distance stay),
 vwaa_off.txt = stop everything.

Switch off without uninstalling: USB stick with an empty vwaa_off.txt.
Uninstall: empty file Mods/VW_AA_ClusterNav/uninstall.txt, run the card again.
Other markers: vwaa_plain, vwaa_nohide, vwaa_no838, vwaa_nosnoop, vwaa_reset.

Logs/_ModKit_Update.log contains "error: servicemgrmibhigh.sh not found".
That is intended (keeps the unit's existing startup wrapper untouched).

Source and credits are in the Source folder. GPL-3.0; personal, non-commercial use.
