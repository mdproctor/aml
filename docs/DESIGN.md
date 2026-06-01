# casehub-aml — Design

## Architecture

_To be documented._

## Module Structure

| Module | Type | Purpose |
|--------|------|---------|
| _(add modules)_ | | |

## Key Abstractions

_To be documented._

## SPI Contracts

_To be documented._

## Data Model

_To be documented._

## Configuration

### Layer 6 — Trust Routing Policies

Trust routing policies are configured per-capability in `app/src/main/resources/casehub/aml/trust-routing.yaml`, loaded by `ConfigFilePreferenceProvider` from `casehub-platform-config`.

Each capability has five independently tunable fields:

| YAML key | Type | Meaning |
|----------|------|---------|
| `casehubio.aml.trust-routing.threshold` | double | Minimum trust score to route to this capability |
| `casehubio.aml.trust-routing.minimum-observations` | int | Minimum observation count before trust score is meaningful |
| `casehubio.aml.trust-routing.borderline-margin` | double | Score range treated as borderline (workload tie-breaking) |
| `casehubio.aml.trust-routing.blend-factor` | double | Weight of trust score vs workload (0.0 = pure workload, 1.0 = pure trust) |
| `casehubio.aml.trust-routing.floor.investigation-accuracy` | double | Minimum investigation-accuracy dimension score; 0.0 = no floor |

Scope path: `casehubio/aml/trust-routing/<capabilityName>`.

Capabilities without a YAML scope entry return `TrustRoutingPolicy.DEFAULT` (availability routing).

**Adding a new capability:** add a scope entry to `trust-routing.yaml`. No code changes needed.

**Runtime override:** set `casehub.platform.preferences.defaults.casehubio.aml.trust-routing.<field>=<value>` via SmallRye Config (environment variable or `application.properties`). Takes precedence over YAML.
