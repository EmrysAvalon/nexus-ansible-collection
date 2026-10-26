
import groovy.json.JsonSlurper

parsed_args = new JsonSlurper().parseText(args)

// The LDAP classes moved from org.sonatype to com.sonatype in newer Nexus versions
def loadLdapClass(String name) {
    def ldapClass = ['com.sonatype.nexus.ldap.persist', 'org.sonatype.nexus.ldap.persist'].findResult { pkg ->
        try {
            this.class.classLoader.loadClass("${pkg}.${name}")
        } catch (ClassNotFoundException ignored) {
            null
        }
    }
    if (ldapClass == null) {
        throw new ClassNotFoundException("Unable to find LDAP class ${name}")
    }
    return ldapClass
}

def ldapConfigMgrClass = loadLdapClass('LdapConfigurationManager')
def connectionClass = loadLdapClass('entity.Connection')
def connectionHostClass = loadLdapClass('entity.Connection$Host')
def connectionProtocolClass = loadLdapClass('entity.Connection$Protocol')
def mappingClass = loadLdapClass('entity.Mapping')

def ldapConfigMgr = container.lookup(ldapConfigMgrClass.getName())

// Create a new configuration with the given name
def ldapConfig = ldapConfigMgr.newConfiguration()
ldapConfig.setName(parsed_args.name)

// Look for existing config to update and replace the blank one created earlier if found
boolean update = false;
ldapConfigMgr.listLdapServerConfigurations().each {
    if (it.name == parsed_args.name) {
        ldapConfig = it
        update = true
    }
}

// Connection
connection = connectionClass.newInstance()
connection.setHost(connectionHostClass.newInstance(Enum.valueOf(connectionProtocolClass, parsed_args.protocol), parsed_args.hostname, Integer.valueOf(parsed_args.port)))
if (parsed_args.auth == "simple") {
    connection.setAuthScheme("simple")
    connection.setSystemUsername(parsed_args.username)
    try {
        connection.setSystemPassword(parsed_args.password)
    } catch (MissingMethodException) {
        connection.setRawSystemPassword(parsed_args.password)
    }
} else {
    connection.setAuthScheme("none")
}
connection.setSearchBase(parsed_args.search_base)
connection.setConnectionTimeout(30)
connection.setConnectionRetryDelay(300)
connection.setMaxIncidentsCount(3)
connection.setUseTrustStore(Boolean.valueOf(parsed_args.use_trust_store))
ldapConfig.setConnection(connection)


// Mapping
mapping = mappingClass.newInstance()
mapping.setUserBaseDn(parsed_args.user_base_dn)
mapping.setLdapFilter(parsed_args.user_ldap_filter)
mapping.setUserObjectClass(parsed_args.user_object_class)
mapping.setUserIdAttribute(parsed_args.user_id_attribute)
mapping.setUserRealNameAttribute(parsed_args.user_real_name_attribute)
mapping.setEmailAddressAttribute(parsed_args.user_email_attribute)

if (parsed_args.map_groups_as_roles) {
    if(parsed_args.map_groups_as_roles_type == "static"){
        mapping.setLdapGroupsAsRoles(true)
        mapping.setGroupBaseDn(parsed_args.group_base_dn)
        mapping.setGroupObjectClass(parsed_args.group_object_class)
        mapping.setGroupIdAttribute(parsed_args.group_id_attribute)
        mapping.setGroupMemberAttribute(parsed_args.group_member_attribute)
        mapping.setGroupMemberFormat(parsed_args.group_member_format)
    } else if (parsed_args.map_groups_as_roles_type == "dynamic") {
        mapping.setLdapGroupsAsRoles(true)
        mapping.setUserMemberOfAttribute(parsed_args.user_memberof_attribute)
    }
}

mapping.setUserSubtree(parsed_args.user_subtree)
mapping.setGroupSubtree(parsed_args.group_subtree)

ldapConfig.setMapping(mapping)

if (update) {
    ldapConfigMgr.updateLdapServerConfiguration(ldapConfig)
} else {
    ldapConfigMgr.addLdapServerConfiguration(ldapConfig)
}
