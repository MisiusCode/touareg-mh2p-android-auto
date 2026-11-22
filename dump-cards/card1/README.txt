Touareg MH2p firmware dump card
===============================
Copies firmware files from the head unit to this card. Installs nothing.
Built on LawPaul's MH2p SD ModKit (CC BY-NC-SA 4.0, not for commercial use):
https://github.com/LawPaul/MH2p_SD_ModKit

Prepare
1. Format an SD card as FAT32 (32 GB or smaller is easiest). At least 2 GB free.
2. Unzip so that Data, Meta, Mods and Logs sit in the ROOT of the card.
3. SD card write-lock slider must be in the unlocked position.

Run
1. Park outside or in a ventilated place, engine running, and keep it running.
2. Unplug the phone from USB.
3. Insert the card. Within about 10 seconds the unit reboots and runs the update.
4. Do not touch the unit. It takes a few minutes and may reboot more than once.
5. Remove the card when the unit asks for it.

Send back
- the whole Dump folder
- the whole Logs folder

Options (empty text files placed in Mods/Touareg_Dump/)
- full_dump.txt     also copy the whole /mnt/app/eso tree. Much larger and slower;
                    only use it if asked.
- allow_modkit.txt  let the ModKit install its normal startup hook. Not needed for a dump.

Expected log line
Logs/_ModKit_Update.log will contain "error: servicemgrmibhigh.sh not found".
That is intended: it is how this card stops the ModKit from installing its hook.
