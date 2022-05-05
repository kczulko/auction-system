# auction-system

## run howto

1. Start daml: `daml start --sandbox-port 7600 --start-navigator false`
1. Start `sbt` (interactive mode) within three terminal windows.
1. Execute following commands:
```bash
# terminal one, Seller's terminal
sbt:auction-system> run -s "Jacek Malczewski, Vicious Circle"

# terminal two, Alice's terminal
sbt:auction-system> run -b alice

# terminal three, Bob's terminal
sbt:auction-system> run -b bob
```
1. Send invitation from the `Seller` terminal. Accept it under `Alice` and `Bob` windows, and submit some bids.
