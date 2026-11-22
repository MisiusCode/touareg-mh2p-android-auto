Touareg MH2p dump card 4 (system config + full copy)
====================================================
Reads the real system config partition and makes a full copy of the app
partition. Installs nothing. Built on LawPaul's MH2p SD ModKit
(CC BY-NC-SA 4.0, not for commercial use).

Prepare
1. Copy Dump3 and Logs to your PC first. Dump3 is your backup; do not lose it.
2. Format the card as FAT32 (at least 4 GB free).
3. Unzip so that Data, Meta, Mods and Logs sit in the ROOT of the card.

Run
1. Engine running and kept running, phone unplugged.
2. Insert the card and leave the unit alone until it asks you to remove it.
   The full copy can take a long time (possibly 30 minutes or more if it
   falls back to copying file by file). If the unit reboots early, send
   what is on the card: the config part is written first.

Afterwards
- Copy Dump4 and Logs to your PC, then share them.

Options (empty text file placed in Mods/Touareg_Dump4/)
- no_full.txt   only read the system config, skip the full copy (a short run)

Expected log line
Logs/_ModKit_Update.log will contain "error: servicemgrmibhigh.sh not found".
That is intended: it is how this card stops the ModKit from installing its hook.
