import com.clearboxmedia.couchdb.cfg.CouchDomainConfiguration

dataSource {
    pooled = true
    driverClassName = "org.hsqldb.jdbcDriver"
    username = "sa"
    password = ""
    configClass = CouchDomainConfiguration.class
}

hibernate {
    cache.use_second_level_cache = true
    cache.use_query_cache = true
    cache.provider_class = 'com.opensymphony.oscache.hibernate.OSCacheProvider'
}

couchdb {
    host = "localhost"
    port = 5984
}

// environment specific settings
environments {
    development {
        dataSource {
            dbCreate = "create-drop" // one of 'create', 'create-drop','update'
            url = "jdbc:hsqldb:mem:devDB"
        }
        couchdb {
            database = "gorm-couchdb-dev"
        }
    }
    test {
        dataSource {
            dbCreate = "update"
            url = "jdbc:hsqldb:mem:testDb"
        }
        couchdb {
            database = "gorm-couchdb-test"
        }
    }
    production {
        dataSource {
            dbCreate = "update"
            url = "jdbc:hsqldb:file:prodDb;shutdown=true"
        }
        couchdb {
            database = "gorm-couchdb"
        }
    }
}