# 1.1.2

Tagged Homes+ system messages (such as `/listhomes`) with an insertion marker so client filters like No Text Wall can leave them visible. No gameplay change.

# 1.1.1

Fixed `/listhomes` (and `/sethome`, `/home`, and `/delhome` confirmations) showing no chat output for regular players when the `sendCommandFeedback` gamerule is false. Command results are now sent as system messages so they always appear. `/listhomes <player>` remains op-only.
