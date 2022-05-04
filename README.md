# auction-system

## run howto

1. Start daml: `daml start --sandbox-port 7600 --start-navigator false`
1. Start `sbt` (interactive mode) within three terminal windows.
1. Execute following command
```bash
# window one, Seller terminal
sbt:auction-system> run -s "Jacek Malczewski, Vicious Circle"

# window two, Alice' terminal
sbt:auction-system> run -b alice

# window three, Bob's terminal
sbt:auction-system> run -b bob
```
1. Send invitation from the `Seller` terminal and submit bids for either `Alice` or `Bob`
