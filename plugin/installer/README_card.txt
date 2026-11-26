VW_AA_ClusterNav 0.7.1 - Android Auto turn data on the cluster and HUD
====================================================================
VW Touareg CR, firmware MH2p_ER_VWG36_P2869 only. Experimental.

New in 0.7.1 (0.6 plus two read-only recorders for the live-map project)
 - the plugin logs which layers the cluster display shows (listens only)
 - the card writes Logs/partb_info.txt: free space and file lists (reads only)

From 0.6 (the everyday version)
 0.5 showed the HUD arrow cannot be fed through the existing services: the
 HUD picture service answers view-style changes only and never takes Android
 Auto content. 0.6
 - turns that experiment off (no more style changes on every drive)
 - keeps street name and distance on the HUD, and hides the
   "Navigation on mobile device is active" message
 - keeps its log in two files used in turn, so a long drive no longer wipes
   the beginning of the log

Install (replaces 0.5 / 0.6, same steps)
 1. Format the card FAT32, unzip so Data, Meta, Mods and Logs are in the ROOT.
 2. Engine running, phone unplugged, insert, wait for the prompt to remove.
 3. Logs/VW_AA_ClusterNav.log must end with "done: installed".

 Markers on a USB stick: vwaa_probe.txt = run the 0.5 HUD experiment again,
 vwaa_off.txt = stop everything.

Switch off without uninstalling: USB stick with an empty vwaa_off.txt.
Uninstall: empty file Mods/VW_AA_ClusterNav/uninstall.txt, run the card again.
Other markers: vwaa_plain, vwaa_nohide, vwaa_no838, vwaa_nosnoop, vwaa_reset.

Logs/_ModKit_Update.log contains "error: servicemgrmibhigh.sh not found".
That is intended (keeps the unit's existing startup wrapper untouched).

Source and credits are in the Source folder. GPL-3.0; personal, non-commercial use.
