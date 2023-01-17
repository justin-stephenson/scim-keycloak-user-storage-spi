package keycloak.scim_user_spi.schemas;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/*
 * {"integration_domain_url":"https://client.ipa.test","name":"ipa.test",
 * "description":"testdescription","client_id":"admin","client_secret":"Secret123",
 * "id_provider":"IPA","user_extra_attrs":"mail:mail, sn:sn, givenname:givenname",
 * "ldap_tls_cacert":"/path/to/cert.pem"}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
	"integration_domain_url",
	"name",
	"description",
	"client_id",
	"client_secret",
	"id_provider",
	"user_extra_attrs",
	"ldap_tls_cacert"
})
@Generated("jsonschema2pojo")
public class IntegrationDomain {

	@JsonProperty("integration_domain_url")
	private String integrationDomainUrl;
	@JsonProperty("name")
	private String name;
	@JsonProperty("description")
	private String description;
	@JsonProperty("client_id")
	private String clientId;
	@JsonProperty("client_secret")
	private String clientSecret;
	@JsonProperty("id_provider")
	private String idProvider;
	@JsonProperty("user_extra_attrs")
	private String userExtraAttrs;
	@JsonProperty("ldap_tls_cacert")
	private String ldapTlsCacert;
	@JsonIgnore
	private Map<String, Object> additionalProperties = new HashMap<String, Object>();

	@JsonProperty("integration_domain_url")
	public String getIntegrationDomainUrl() {
		return integrationDomainUrl;
	}

	@JsonProperty("integration_domain_url")
	public void setIntegrationDomainUrl(String integrationDomainUrl) {
		this.integrationDomainUrl = integrationDomainUrl;
	}

	@JsonProperty("name")
	public String getName() {
		return name;
	}

	@JsonProperty("name")
	public void setName(String name) {
		this.name = name;
	}

	@JsonProperty("description")
	public String getDescription() {
		return description;
	}

	@JsonProperty("description")
	public void setDescription(String description) {
		this.description = description;
	}

	@JsonProperty("client_id")
	public String getClientId() {
		return clientId;
	}

	@JsonProperty("client_id")
	public void setClientId(String clientId) {
		this.clientId = clientId;
	}

	@JsonProperty("client_secret")
	public String getClientSecret() {
		return clientSecret;
	}

	@JsonProperty("client_secret")
	public void setClientSecret(String clientSecret) {
		this.clientSecret = clientSecret;
	}

	@JsonProperty("id_provider")
	public String getIdProvider() {
		return idProvider;
	}

	@JsonProperty("id_provider")
	public void setIdProvider(String idProvider) {
		this.idProvider = idProvider;
	}

	@JsonProperty("user_extra_attrs")
	public String getUserExtraAttrs() {
		return userExtraAttrs;
	}

	@JsonProperty("user_extra_attrs")
	public void setUserExtraAttrs(String userExtraAttrs) {
		this.userExtraAttrs = userExtraAttrs;
	}

	@JsonProperty("ldap_tls_cacert")
	public String getLdapTlsCacert() {
		return ldapTlsCacert;
	}

	@JsonProperty("ldap_tls_cacert")
	public void setLdapTlsCacert(String ldapTlsCacert) {
		this.ldapTlsCacert = ldapTlsCacert;
	}

	@JsonAnyGetter
	public Map<String, Object> getAdditionalProperties() {
		return this.additionalProperties;
	}

	@JsonAnySetter
	public void setAdditionalProperty(String name, Object value) {
		this.additionalProperties.put(name, value);
	}

}
