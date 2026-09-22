package lk.aora.equipmentmanagement.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

        @Value("${auth0.domain}")
        private String auth0Domain;

        @Value("${auth0.apiAudience}")
        private String apiAudience;

    @Bean
    public OpenAPI customOpenAPI() {

        return new OpenAPI()
                .info(new Info()
                        .title("Aora Equipment Management API")
                        .version("v1.0")
                        .description("REST API for Aora Equipment Management System"))

                .addSecurityItem(
                        new SecurityRequirement()
                                .addList("oauth2")
                )

                .components(new Components()
                        .addSecuritySchemes(
                                "oauth2",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.OAUTH2)
                                        .description("Auth0 Authentication")
                                        .flows(
                                                new OAuthFlows()
                                                        .authorizationCode(
                                                            new OAuthFlow()
                                                                .authorizationUrl(
                                                                                                                                        "https://" + auth0Domain
                                                                                                                                                + "/authorize?audience=" + apiAudience
                                                                )
                                                                .tokenUrl(
                                                                                                                                        "https://" + auth0Domain + "/oauth/token"
                                                                )
                                                        )
                                        )
                        )
                );
    }
}

