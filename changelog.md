# Open Nyzo verifier changelog

Version is an integer. Open Nyzo uses MMMNNN format, where MMM is the main version number and NNN the Open nyzo subversion number. 

599001 is the recommended version to run if you want to test the "no more red in-queue verifiers".  
It's based upon official 599.

## Switching to Open Nyzo branch

This is experimental. I did test myself, other Zonys do run that version as well on in-cycle and in-queue verifiers. We think this version is no more unstable than the official version.  
The changes in code are purposedly minimal and easy to check.  

However - until this is merged into the official repo - running this version is your responsability to take. 

From an existing install:
```
rm -rd nyzoVerifier 
git clone https://github.com/Open-Nyzo/nyzoVerifier.git
cd nyzoVerifier
./gradlew build
supervisorctl reload
```

On a blank setup, just use that open nyzo git link `https://github.com/Open-Nyzo/nyzoVerifier.git` instead of the official nyzo one and keep the usual install instructions.

If you want a clone setup and compare the drops, then don't forget to also duplicate `/var/lib/nyzo/production/nodes` from old to new install so it does not start empty.

**Update**  
Since these mods are now a Pull Request vs the official repo, you can easily switch version via git, with official repo only:

From your user dir (either /home/ubuntu or /root depending on your install):

```
cd nyzoVerifier
git fetch origin pull/25/head:NCFP10
git checkout NCFP10
./gradlew build
supervisorctl reload
```

You'll end up on 599001

## 599001

599001 is the same mod as 595001+595002 applied to base version 599.

## 597001

597001 is the same mod as 595001+595002 applied to base version 597.

## 595002

v572 introduced communicationFailureCount for Node: https://tech.nyzo.co/releaseNotes/v572

Purpose of communicationFailureCount is to record the number of successive communication attempts failures and mark the peer verifier "inactive" once it reaches a given threshold (6 fails atm).    
This important data (in regard to red in-queue verifiers) was not persisted.

This means that when restarting a verifier, communicationFailureCount where set at 0 for everyone, no matter the previous count.    
Since it takes minimum     
- 2 cycles for a restarted verifier to begin checking the queue  
- 6 cycles before marking a verifier inactive

Restarting  a verifier means it will not drop any red verifier for at least 8 cycles.   

This version does persist the communicationFailureCount across restarts.

> The unused field from v572 is re-used for that purpose, and ascending compatibility is kept.

At restart, there will still be the 2 cycles wait before testing queue, but then counts will go on from previous state.

As a result, queue clean up will be more efficient and faster, plus spamming/killing nodes to have them restart and thus reset will be a way less effective method.

**Impact on ressources:** None. Uses existing methods, files and field, no sensible impact on verifier load.


Additionnaly, 595002 includes more verbose logging for nodejoin messages.   
By logging ip and short id of nodejoin requests, external scripts and tools are able to automatically id the ips responsible for heavy spamming of nodejoin messages and ban them.
> It's recommended to have `log_timestamps=true` in `/var/lib/nyzo/production/preferences` file, so that logs come with timestamps.

Example of such log line:  
`[1595771718.408 (2020-07-26 13:55:18.408 UTC)]: nodejoin_from 46.161.12.140 0049...27cb cookieA12140`

> Please note that nodejoins are a normal part of the protocol and not harmful as such. High volume of such messages however is spam and to be considered as an attack.



### Upgrading

Nodes running 595001 should *not* restart just to upgrade (would mean 8 cycles loss), however if you plan to restart a 595001 node, as well upgrade to 595002 before restarting.  



## 595001

First attempt at mitigating red in-queue verifiers.    
 
See https://github.com/n-y-z-o/nyzoVerifier/issues/22  
This addresses Proposal #1 and is the most minimal answer to the abuses as acknowledged by NCP-10.

Comparison:  
https://github.com/n-y-z-o/nyzoVerifier/compare/master...Open-Nyzo:master

Main thing here is making sure communicationFailureCount is only reset in answer to a request, and not accessible to fake verifiers only spamming nodejoins messages once in "nodes" file.  
Seems pretty effective so far, already under testing by several in-cycle verifiers. 

- Nyzo.today sortable list of dropped verifiers https://nyzo.today/dropped
- Nyzo.today "clean" queue (auto cleaning process still ongoing) https://nyzo.today/queue

> TODO: Integrate more info about current working and the differences. 

