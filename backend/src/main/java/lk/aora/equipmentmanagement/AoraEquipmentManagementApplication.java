package lk.aora.equipmentmanagement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableJpaAuditing
@EnableTransactionManagement
@ConfigurationPropertiesScan
public class AoraEquipmentManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(AoraEquipmentManagementApplication.class, args);
    }
}