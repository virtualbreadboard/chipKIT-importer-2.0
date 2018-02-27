# chipKIT-importer-2.0
## Next generation implementation, adding Atmel-based Arduino boards

The importer plugin is still work-in-progress but there is a automated regression test suite that can perform the import operation on a number of boards and various projects. The test must be first configured by editing the config.yml file located under /test/unit/src/regression/config.yml. Some guidance regarding test configuration can be found in the configuration file. For performance reasons, the test executes the "no-copy" import. It can be easily launched from NetBeans IDE just like a normal JUnit test - the test class is called regression.RegressionTest.
