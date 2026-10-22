# Security policy

This is a portfolio and learning project: an online shop with simulated payments
(Stripe only in test mode) and no real customers. Its security is still built the
way a real shop's should be, and [docs/security-design.md](docs/security-design.md)
explains how it works, with a diagram of each flow.

## Reporting a security problem

Please don't open a public issue, pull request or discussion about it. Report it
privately instead, with GitHub's
[Report a vulnerability](https://github.com/Aryankanani1/shopping-cart-spring-security/security/advisories/new)
form, on the repository's **Security** tab. Include what is affected, the steps to
reproduce it, and what someone could do with it.

The report stays private. The fix is prepared in a private security advisory, and
the problem is described in public only after the fix is deployed.

A vulnerability in a dependency that already has a published CVE is different: the
pipeline's scans and Dependabot track those, and an ordinary issue is fine.

## What is covered

The latest `master`. Tags such as `security-design-v1.0` are snapshots of the
documentation, for learning, not supported releases.
