package org.springframework.samples.petclinic.customers.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OwnerTest {

    @Test
    void toStringShouldNotContainPii() {
        Owner owner = new Owner();
        owner.setFirstName("John");
        owner.setLastName("Doe");
        owner.setAddress("123 Main St");
        owner.setCity("Springfield");
        owner.setTelephone("5551234567");

        String result = owner.toString();

        assertThat(result).doesNotContain("John");
        assertThat(result).doesNotContain("Doe");
        assertThat(result).doesNotContain("123 Main St");
        assertThat(result).doesNotContain("Springfield");
        assertThat(result).doesNotContain("5551234567");
    }
}
