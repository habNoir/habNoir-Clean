package xyz.habnoir.clean.engine.oem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OemSpecRegistryTest {

    @Test
    fun `resolves all Indonesian OEM families accurately`() {
        assertEquals(
            OemFamily.XIAOMI_HYPEROS_MIUI,
            OemSpecRegistry.resolveFamily("Xiaomi", "Redmi") { false }
        )
        assertEquals(
            OemFamily.OPPO_REALME_COLOROS,
            OemSpecRegistry.resolveFamily("OPPO", "realme") { false }
        )
        assertEquals(
            OemFamily.VIVO_IQOO_FUNTOUCH,
            OemSpecRegistry.resolveFamily("vivo", "iQOO") { false }
        )
        assertEquals(
            OemFamily.TRANSSION_XOS_HIOS,
            OemSpecRegistry.resolveFamily("INFINIX", "Infinix") { false }
        )
        assertEquals(
            OemFamily.TRANSSION_XOS_HIOS,
            OemSpecRegistry.resolveFamily("TECNO", "TECNO") { false }
        )
        assertEquals(
            OemFamily.TRANSSION_XOS_HIOS,
            OemSpecRegistry.resolveFamily("itel", "itel") { false }
        )
        assertEquals(
            OemFamily.SAMSUNG_ONEUI,
            OemSpecRegistry.resolveFamily("samsung", "samsung") { false }
        )
        assertEquals(
            OemFamily.AOSP_GENERIC,
            OemSpecRegistry.resolveFamily("Google", "Pixel") { false }
        )
    }

    @Test
    fun `samsung spec contains Hapus memori label`() {
        val spec = OemSpecRegistry.specFor(OemFamily.SAMSUNG_ONEUI)
        assertTrue(spec.clearCacheButtonLabels.contains("hapus memori"))
    }

    @Test
    fun `resolves accurate device identity per Indonesian brand and OS`() {
        val tecno = OemSpecRegistry.resolveDeviceIdentity(
            manufacturer = "TECNO",
            brand = "TECNO",
            model = "TECNO CN7c",
            family = OemFamily.TRANSSION_XOS_HIOS,
            sdkInt = 36,
            androidRelease = "16"
        )
        assertEquals("TECNO", tecno.brandBadge)
        assertEquals("TECNO HiOS", tecno.headerLabel)
        assertEquals("TECNO CN7c (HiOS, Android 16)", tecno.fullDisplay)

        val infinix = OemSpecRegistry.resolveDeviceIdentity(
            manufacturer = "INFINIX",
            brand = "Infinix",
            model = "X6851",
            family = OemFamily.TRANSSION_XOS_HIOS,
            sdkInt = 35,
            androidRelease = "15"
        )
        assertEquals("Infinix XOS", infinix.headerLabel)
        assertEquals("Infinix X6851 (XOS, Android 15)", infinix.fullDisplay)

        val redmi = OemSpecRegistry.resolveDeviceIdentity(
            manufacturer = "Xiaomi",
            brand = "Redmi",
            model = "23124RA7EO",
            family = OemFamily.XIAOMI_HYPEROS_MIUI,
            sdkInt = 35,
            androidRelease = "15"
        )
        assertEquals("Redmi HyperOS", redmi.headerLabel)

        val realme = OemSpecRegistry.resolveDeviceIdentity(
            manufacturer = "OPPO",
            brand = "realme",
            model = "RMX3840",
            family = OemFamily.OPPO_REALME_COLOROS,
            sdkInt = 35,
            androidRelease = "15"
        )
        assertEquals("realme UI", realme.headerLabel)

        val samsung = OemSpecRegistry.resolveDeviceIdentity(
            manufacturer = "samsung",
            brand = "samsung",
            model = "SM-A556E",
            family = OemFamily.SAMSUNG_ONEUI,
            sdkInt = 35,
            androidRelease = "15"
        )
        assertEquals("Samsung One UI", samsung.headerLabel)
    }
}
