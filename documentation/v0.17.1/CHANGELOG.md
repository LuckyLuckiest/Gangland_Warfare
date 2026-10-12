# 0.17.1 Changelog

[← Back to Documentation Index](../README.md)

---

## 🐛 Fixes

- **Disbanding a gang no longer loses an online member's share.** If an online member was missing from the plugin's member cache, their disband payout stayed in the deleted gang's bank. The payout now uses the gang's own member record. (T-279)
