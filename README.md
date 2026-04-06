# Environment Monitoring Plugin – SailPoint IdentityIQ

Een IIQ-plugin die periodiek de gezondheid van uw omgeving controleert en de resultaten via een configureerbare web service API verstuurt.

---

## Functionaliteiten

| Check | Beschrijving |
|---|---|
| **HealthPing** | Verifieert dat IIQ en de database bereikbaar zijn |
| **FailedTasks** | Detecteert TaskResults met status Error/Terminated (afgelopen 24 uur) |
| **FailedProvisioning** | Detecteert gefaalde ProvisioningTransactions en IdentityRequests |
| **ProvisioningDelta** | Vergelijkt het provisioning-volume van vandaag met gisteren |
| **ApplicationHealth** | Voert `testConfiguration()` uit op alle (of geselecteerde) Applications |
| **BeanshellCustomCheck** | Voert een door u geschreven BeanShell Rule uit (moet `Boolean` retourneren) |

---

## Vereisten

- SailPoint IdentityIQ 8.x
- Java 8+
- Apache Ant (voor het bouwen)
- De volgende jars aanwezig in `$SAILPOINT_HOME/WEB-INF/lib`:
  - `identityiq.jar`
  - `velocity-*.jar`
  - `httpclient-*.jar` / `httpcore-*.jar`
  - `commons-codec-*.jar`

---

## Bouwen

```bash
export SAILPOINT_HOME=/pfad/naar/identityiq
ant clean build
# → dist/EnvironmentMonitoringPlugin-1.0.zip
```

---

## Installeren

1. Log in als systeembeheerder in IIQ.
2. Ga naar **Gear → Plugin Management → Install Plugin**.
3. Upload `dist/EnvironmentMonitoringPlugin-1.0.zip`.
4. De plugin importeert automatisch:
   - `MonitoringPluginConfig` (Custom object met standaardwaarden)
   - `Environment Monitoring Task` (TaskDefinition)
   - `Environment Monitoring Schedule` (elke 5 minuten)

---

## Configuratie

### Via de Plugin UI

Navigeer naar **Plugin Management → Environment Monitoring Plugin** (of via het menu).  
De configuratie-interface stelt u in staat om alle instellingen in te vullen zonder XML te bewerken.

### Via XML (MonitoringConfig.xml)

Pas `db/scripts/MonitoringConfig.xml` aan vóór de installatie, of bewerk het Custom object `MonitoringPluginConfig` achteraf via de IIQ Debug-pagina.

#### Alle configuratiesleutels

```xml
<!-- Web Service -->
<entry key="webserviceBaseUrl"          value="https://..."/>
<entry key="webserviceUsername"         value="user"/>
<entry key="webservicePassword"         value="geheim"/>
<entry key="webserviceContentType"      value="application/json"/>
<entry key="webserviceTimeoutSeconds"   value="30"/>

<!-- Body template (Apache Velocity) -->
<entry key="bodyTemplate">...</entry>

<!-- Checks aan/uit -->
<entry key="enableHealthPing"           value="true"/>
<entry key="enableFailedTasks"          value="true"/>
<entry key="enableFailedProvisioning"   value="true"/>
<entry key="enableProvisioningDelta"    value="true"/>
<entry key="enableApplicationHealth"    value="true"/>

<!-- Delta drempelwaarden (% daling t.o.v. gisteren) -->
<entry key="provisioningDeltaWarningPct" value="50"/>
<entry key="provisioningDeltaErrorPct"   value="80"/>

<!-- Application Health filter (leeg = alles) -->
<entry key="applicationHealthIncluded"  value="AppA,AppB"/>
<entry key="applicationHealthExcluded"  value="TestApp"/>

<!-- Custom BeanShell Rules: "RuleNaam|Weergavenaam" per List-item -->
<entry key="customRules">
  <value>
    <List>
      <String>MijnHealthRule|Mijn Custom Check</String>
    </List>
  </value>
</entry>
```

---

## Body Template (Apache Velocity)

Het body-template wordt gerenderd met Apache Velocity vóór verzending.

### Beschikbare variabelen

| Variabele | Type | Inhoud |
|---|---|---|
| `$result.overallStatus` | String | `OK`, `WARNING`, `ERROR`, `UNKNOWN` |
| `$now` | String | ISO-8601 timestamp (UTC) |
| `$result.timestamp` | `java.util.Date` | Tijdstip van de meting |
| `$result.checkResults` | `List<CheckResult>` | Alle check-resultaten |
| `$checks.FailedTasks` | `CheckResult` | Direct toegang op naam |

### CheckResult-velden

- `$check.checkName` – naam van de check
- `$check.status` – `OK` / `WARNING` / `ERROR` / `UNKNOWN`
- `$check.message` – tekstuele samenvatting
- `$check.details` – `Map<String,Object>` met extra details

### Voorbeeld – JSON

```velocity
{
  "source": "SailPointIIQ",
  "status": "$result.overallStatus",
  "timestamp": "$now",
  "checks": {
#foreach($check in $result.checkResults)
    "$check.checkName": {
      "status": "$check.status",
      "message": "$check.message"
    }#if($foreach.hasNext),#end
#end
  }
}
```

### Voorbeeld – Platte tekst (bijv. voor Nagios/NRPE)

```velocity
$result.overallStatus | timestamp=$now
#foreach($check in $result.checkResults)
$check.checkName=$check.status ($check.message)
#end
```

---

## Custom BeanShell Rules schrijven

Maak een Rule-object in IIQ met de volgende structuur:

```java
<?xml version='1.0' encoding='UTF-8'?>
<!DOCTYPE Rule PUBLIC "sailpoint.dtd" "sailpoint.dtd">
<Rule name="MijnHealthRule" language="beanshell">
  <Description>Voorbeeld custom monitoring check</Description>
  <Source>
    import sailpoint.api.SailPointContext;
    import sailpoint.object.*;

    // context is beschikbaar als variabele
    try {
        // Uw logica hier...
        // Voorbeeld: controleer of een specifieke identity bestaat
        Identity identity = context.getObjectByName(Identity.class, "serviceAccount");
        return (identity != null && !identity.isInactive());
    } catch (Exception e) {
        return false;
    }
  </Source>
</Rule>
```

**Regels:**
- De Rule **moet** een `Boolean` retourneren (`true` = OK, `false` = ERROR)
- Beschikbare variabele: `context` (SailPointContext)
- Exceptions worden gevangen en resulteren in `Status.ERROR`

---

## Intervalinstelling wijzigen

Pas de `cronExpression` in `MonitoringSchedule.xml` aan, of via **Tasks → Schedules** in IIQ:

| Interval | Cron expressie |
|---|---|
| Elke 5 minuten | `0 0/5 * * * ?` |
| Elke 15 minuten | `0 0/15 * * * ?` |
| Elk uur | `0 0 * * * ?` |
| Elke dag om 06:00 | `0 0 6 * * ?` |

---

## REST API (handmatig aanroepen)

| Methode | URL | Beschrijving |
|---|---|---|
| `GET` | `/iiq/plugin/rest/EnvironmentMonitoringPlugin/monitoring/config` | Huidige configuratie ophalen |
| `PUT` | `/iiq/plugin/rest/EnvironmentMonitoringPlugin/monitoring/config` | Configuratie opslaan |
| `POST` | `/iiq/plugin/rest/EnvironmentMonitoringPlugin/monitoring/runNow` | Checks direct uitvoeren |

---

## Projectstructuur

```
EnvironmentMonitoringPlugin/
├── iiq-plugin.xml                          # Plugin descriptor
├── build.xml                               # Ant build script
├── db/scripts/
│   ├── MonitoringConfig.xml                # Default Custom object
│   └── MonitoringSchedule.xml              # TaskDefinition + Schedule
├── ui/
│   └── index.html                          # Configuratie UI
└── src/com/plugin/monitoring/
    ├── checks/
    │   ├── IMonitoringCheck.java           # Interface
    │   ├── HealthPingCheck.java
    │   ├── FailedTasksCheck.java
    │   ├── FailedProvisioningCheck.java
    │   ├── ProvisioningDeltaCheck.java
    │   ├── ApplicationHealthCheck.java
    │   └── BeanshellCustomCheck.java
    ├── model/
    │   └── MonitoringResult.java
    ├── service/
    │   ├── MonitoringOrchestrator.java
    │   ├── VelocityTemplateService.java
    │   └── WebServiceSenderService.java
    ├── task/
    │   └── MonitoringTask.java
    └── rest/
        └── MonitoringConfigResource.java
```
