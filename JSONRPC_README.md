# Nyzo verifier JSON-RPC add-on

## About RPC Commands Project
This Verifier supports JSON-RPC which makes much easier for Exchanges to list Nyzo.

Reminder: Most of the rpc commands are different from other cryptocurrency.  
Exchanges may need to adjust bit to support it.

## Guides for using RPC Server:

* [How to use the Yandev RPC server for Nyzo](https://medium.com/@nyzoco/how-to-use-the-yandev-rpc-server-for-nyzo-56e616e0e6d0)

## Testing

Exemple from bash using curl to call the "info" command:  
`curl -X POST http://127.0.0.1:4000/jsonrpc -H 'Cache-Control: no-cache' -H 'Content-Type: application/json' -d '{"jsonrpc": "2.0", "method": "info", "id": 1}'`

Sample censored answer:  
```
{
  "result":{
    "retention_edge":-1,
    "identifier":"0000000000000000-0000000000000000-0000000000000000-0000000000000000",
    "voting_pool_size":9389,
    "trailing_edge":-1,
    "nickname":"Test",
    "block_creation_information":"0/0",
    "cycle_length":2442,
    "transaction_pool_size":0,
    "version":606010,
    "frozen_edge":10484424,
    "nyzo_string":"id__test_censored_info"
  },
  "id":1,
  "jsonrpc":"2.0"
}
```

# Changelog
All version by YanDevDe are being displayed as [nyzo version].[my version]

Since integration in Open Nyzo, version follow the NNN0R0 format, with NNN = main Nyzo code version and R = RPC version relative to main Nyzo version.

##  Version 606010

- Import into Open Nyzo repo
- Update main code to version 606
- Doc consolidation

##  Version 578.1

- Update to version 578.1

##  Version 573.1

- Update to version 573

##  Version 569.1

- Update to version 569

##  Version 558.1

- Update to version 558

##  Version 550.1

### Method `info`:
##### Return:
- Additional "nyzo_string" for own public NyzoString

### Method `balance`:
##### Sending Data:
- It can have either "identifier" or "nyzo_string", one of the both must be entered.

### Method `broadcast`:
##### Sending Data:
- It can have either "receiver_identifier" or "receiver_nyzo_string" (NyzoString), one of the both must be entered.
-  It can have either "sender_identifier" or "sender_nyzo_string" (NyzoString), one of the both must be entered.
- It can have either "private_seed" (private identifier) or "private_nyzo_string" (private NyzoString), one of the both must be entered.

##### Return:
- "receiver_nyzo_string" and "sender_nyzo_string" added, even if you sent only identifier for receiver and sender

### Method `cycleinfo`:
##### Return:
- Additional "nyzo_string" for node public NyzoString

### Method `block`:
##### Return:
- Additional "sender_nyzo_string" for public sender NyzoString from block
- Additional "receiver_nyzo_string" for public receiver NyzoString from block


# Donations
YanDevDe aka Chinafreak Nyzo Donation:  
Public Nyzo String:`id__87v7G1WX91GrzT-30uHMQz4VSZaWRgrouzI0Hx651AXLJHrxP0sZ`

