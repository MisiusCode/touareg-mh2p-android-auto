Touareg MH2p dump card 3 (backup-grade dump)
============================================
Copies programs, libraries, config files and a full copy of the app partition
to this card. Installs nothing. Built on LawPaul's MH2p SD ModKit
(CC BY-NC-SA 4.0, not for commercial use).

Prepare
1. Copy Dump2 and Logs off the card first if you want to keep them.
2. Format the card as FAT32. It needs at least 4 GB free.
3. Unzip so that Data, Meta, Mods and Logs sit in the ROOT of the card.

Run
1. Engine running and kept running, phone unplugged.
2. Insert the card and leave the unit alone until it asks you to remove it.
   This run is longer than the earlier ones because of the full copy
   (expect 10 to 30 minutes; the time is an estimate).
3. If it stops or reboots early, send what is on the card anyway: the small
   and important parts are written first.

Afterwards
- Copy the whole Dump3 folder and Logs to your PC. This is your backup.
- Dump3/private holds licence and settings data bound to this car.
  Keep it. Do not share or upload it.
- Share Dump3/files, Dump3/lists, Dump3/info.txt and Logs.

Options (empty text file placed in Mods/Touareg_Dump3/)
- no_full.txt   skip the full copy of the app partition

Expected log line
Logs/_ModKit_Update.log will contain "error: servicemgrmibhigh.sh not found".
That is intended: it is how this card stops the ModKit from installing its hook.
