## Release 3.0.0

* Use Edison 2.0.0-rc7, Spring Boot 2.1.6 and recent libs to make edison-jobtrigger run with Java > 8

## Snapshot 2.1.0

* Add LDAP-Authorization: New properties edison.jobtrigger.security.basicAuthUser and edison.jobtrigger.security.basicAuthPasswd
* Add possibility to set values for version.properties via environment variables instead of git information

## Release 2.0.0

* Updated to edison-microservice 1.0.0
* Use ConfigurationProperties
* Bugfix: configure scheduler pool size
* Make size of scheduler pool configurable through edison.jobtrigger.scheduler.poolsize
* Bugfix: discover for notTriggerableJobDefintions
* Make size of jobresult history configurable through edison.jobtrigger.jobresults.max

## Release 1.1.0

* Updated to edison-microservice 0.48.0

## Release 1.0.0

* Initial Release
