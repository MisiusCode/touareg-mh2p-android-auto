Touareg MH2p dump card 2 (identify the existing patch)
======================================================
Copies the existing patch's files and file listings to this card. Installs nothing.
Built on LawPaul's MH2p SD ModKit (CC BY-NC-SA 4.0, not for commercial use).

1. Format the SD card as FAT32 (this erases the first dump; keep a copy if you want it).
2. Unzip so that Data, Meta, Mods and Logs sit in the ROOT of the card.
3. Engine running, phone unplugged, insert the card, leave the unit alone
   until it asks you to remove the card.
4. Send back the Dump2 and Logs folders.

Logs/_ModKit_Update.log will contain "error: servicemgrmibhigh.sh not found".
That is intended: it is how this card stops the ModKit from installing its hook.
