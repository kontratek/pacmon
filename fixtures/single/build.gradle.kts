plugins {
    java
}

dependencies {
    implementation("org.slf4j:slf4j-api:2.0.17")
    testImplementation(libs.junit.jupiter)
    runtimeOnly("org.postgresql:postgresql:42.7.5")
}
