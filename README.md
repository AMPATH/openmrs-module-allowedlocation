# Allowed Location Module

An [OpenMRS](https://openmrs.org) module that extends the
[Data Filter module](https://github.com/openmrs/openmrs-module-datafilter) to scope
`org.openmrs.Location` — including the login location picker — to the locations listed in a
user's `allowed_location` user property, as a lighter-weight alternative to granting per-user
rows in Data Filter's `datafilter_entity_basis_map` table.

## Why

Data Filter's built-in location filter grants access through `datafilter_entity_basis_map`,
which requires a row per user per location. For deployments that already track a user's
allowed locations elsewhere (a comma separated user property, synced from an external system,
say), maintaining a parallel set of basis map rows is redundant. This module reads that
property directly instead.

Only `org.openmrs.Location` is scoped this way. Data Filter's own location-based filters for
patients and their clinical data keep reading `datafilter_entity_basis_map`, because its
`AccessInterceptor` independently re-checks those types against that table — driving them from
a user property here would let the filter and the interceptor disagree.

## How it works

- `AllowedLocationAccessUtil.getAccessibleLocations()` resolves the authenticated user's
  `allowed_location` user property (comma separated location uuids or names) to a set of
  location ids, including their descendants unless configured not to. This is the module's
  reusable entry point, see [Reusing this from another module](#reusing-this-from-another-module).
- `AllowedLocationFilterListener` is a `DataFilterListener`, discovered by Data Filter across
  every module's classpath the same way it discovers its own built-in listeners. On each
  session it binds what that util resolved to the filter's parameter.
- `allowed_location.json` registers a Hibernate filter that restricts `org.openmrs.Location` to
  `location_id IN (:allowedLocationIds)`, picked up by Data Filter's
  `classpath*:/filters/hibernate/*.json` scan without any change to Data Filter itself.
- A user with no value for the property is unrestricted by default, so installing the module
  does not change what existing users can see until they're actually given the property. A
  super user is never restricted.
- A user whose property is set but resolves to nothing is denied every location (fails closed),
  rather than falling back to unrestricted access.
- On install, the module seeds `datafilter_locationFilter.disabled=true` so Data Filter's own
  basis-map-based location filter isn't ANDed against this one by default (a user with no basis
  map rows would otherwise be restricted to zero locations regardless of what this module
  resolves). An admin who deliberately wants both filters enforced together — basis map grants
  as a hard ceiling on top of the user property — can flip that global property back to `false`.

## Configuration

Global properties, all optional:

| Property | Default | Description |
| --- | --- | --- |
| `allowedlocation.userProperty` | `allowed_location` | Name of the user property read for the comma separated list of location uuids (or names). |
| `allowedlocation.includeDescendants` | `true` | Whether child locations of each allowed location are accessible too, at every level of nesting. |
| `allowedlocation.unrestrictedWhenUnset` | `true` | Whether a user with no value for the user property is left unrestricted. Set to `false` once every user has the property, to fail closed instead. |
| `datafilter_locationFilter.disabled` | `true` (seeded on install) | Belongs to Data Filter, not this module. Kept `true` so Data Filter's basis-map-based location filter doesn't AND against this module's filter. |

## Reusing this from another module

Another module that wants to scope *its* data to the same locations should not read the user
property again — resolving it twice means two sets of rules and two sets of global properties that
can disagree. Instead, register a Hibernate filter for your own tables with Data Filter and set its
parameter from this module:

```java
@Component("myLocationFilterListener")
@OpenmrsProfile(modules = { "datafilter:2.2.0 - 2.*" })
public class MyLocationFilterListener implements DataFilterListener {

    @Override
    public boolean supports(String filterName) {
        return "mymodule_locationBasedFilter".equals(filterName);
    }

    @Override
    public boolean onEnableFilter(DataFilterContext filterContext) {
        AllowedLocationAccess access = AllowedLocationAccessUtil.getAccessibleLocations();
        if (!access.isRestricted()) {
            return false;
        }

        filterContext.setParameter("allowedLocationIds", AllowedLocationAccessUtil.asFilterParameter(access));

        return true;
    }
}
```

Two things are worth knowing before you do:

- `isRestricted()` false and an empty set of location ids are different answers. The first means
  the user is not scoped by location at all and your filter should stay disabled; the second means
  they are allowed nothing, and `asFilterParameter` turns it into an id that matches no row so the
  filter fails closed. Never bind an empty collection, it renders as an invalid `IN ()`.
- Data Filter enables every registered filter on each session and expects a listener to set its
  parameters, so a registration whose listener is missing leaves an enabled filter with an unset
  parameter, which Hibernate rejects on the next query. Register the filter and its listener
  together, in the module that owns the tables being scoped, and gate the listener bean on Data
  Filter being present rather than on this module.

The billing module's `billing_locationBasedBillingFilter` works exactly this way: it declares the
filter on its own Hibernate mappings, because Data Filter cannot add filter tags to the mapping
files a module contributes, and delegates the resolution here.

## Requirements

- OpenMRS Platform 2.8.9+
- [Data Filter module](https://github.com/openmrs/openmrs-module-datafilter) 2.2.0+
- On Java 9 and later, the server needs `--add-opens java.base/java.lang=ALL-UNNAMED` and
  `--add-opens java.base/java.lang.reflect=ALL-UNNAMED`. Data Filter adds Hibernate's filter
  annotations to entity classes at startup by reflecting on `java.lang.Class`, which the module
  system denies without those flags; it fails to start rather than silently skipping the filters.
  The same flags are set for the tests here, see the surefire configuration in the root `pom.xml`.

## Building

```
mvn clean install
```

## License

[MPL 2.0 with Healthcare Disclaimer](https://openmrs.org/license), consistent with other
OpenMRS modules.
