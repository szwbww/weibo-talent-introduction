package com.weibo.talentintroduction.expert.domain

import java.util.Locale

object CountryContinentMapping {
    const val REGION_CHINA = "China"
    const val REGION_ASIA_JK = "Asia (Japan & Korea)"
    const val REGION_ASIA_OTHER = "Asia (Other)"
    const val REGION_EUROPE = "Europe"
    const val REGION_NORTH_AMERICA = "North America"
    const val REGION_SOUTH_AMERICA = "South America"
    const val REGION_AFRICA = "Africa"
    const val REGION_OCEANIA = "Oceania"
    const val REGION_OTHER = "Other"

    private val REGION_ORDER = listOf(
        REGION_CHINA,
        REGION_ASIA_JK,
        REGION_ASIA_OTHER,
        REGION_EUROPE,
        REGION_NORTH_AMERICA,
        REGION_SOUTH_AMERICA,
        REGION_AFRICA,
        REGION_OCEANIA,
        REGION_OTHER
    )

    private val MAPPING: Map<String, String> = buildMap {
        fun mapEntries(region: String, keys: Collection<String>) {
            keys.forEach { put(it.lowercase(Locale.ROOT), region) }
        }

        mapEntries(REGION_CHINA, listOf(
            "china", "cn", "chinese", "people's republic of china", "prc",
            "hong kong", "hk", "macau", "macao", "taiwan", "tw"
        ))

        mapEntries(REGION_ASIA_JK, listOf(
            "japan", "jp", "japanese",
            "korea", "kr", "south korea", "republic of korea", "korean",
            "north korea", "kp", "dprk"
        ))

        mapEntries(REGION_ASIA_OTHER, listOf(
            "afghanistan", "af",
            "armenia", "am",
            "azerbaijan", "az",
            "bahrain", "bh",
            "bangladesh", "bd",
            "bhutan", "bt",
            "brunei", "bn", "brunei darussalam",
            "cambodia", "kh",
            "cyprus", "cy",
            "georgia", "ge",
            "india", "in", "indian",
            "indonesia", "id",
            "iran", "ir", "islamic republic of iran",
            "iraq", "iq",
            "israel", "il",
            "jordan", "jo",
            "kazakhstan", "kz",
            "kuwait", "kw",
            "kyrgyzstan", "kg",
            "laos", "la", "lao people's democratic republic",
            "lebanon", "lb",
            "malaysia", "my",
            "maldives", "mv",
            "mongolia", "mn",
            "myanmar", "mm", "burma",
            "nepal", "np",
            "oman", "om",
            "pakistan", "pk",
            "palestine", "ps", "palestinian territory",
            "philippines", "ph",
            "qatar", "qa",
            "saudi arabia", "sa",
            "singapore", "sg",
            "sri lanka", "lk",
            "syria", "sy",
            "tajikistan", "tj",
            "thailand", "th",
            "timor-leste", "tl", "east timor",
            "turkey", "tr", "türkiye",
            "turkmenistan", "tm",
            "united arab emirates", "ae", "uae",
            "uzbekistan", "uz",
            "vietnam", "vn", "viet nam",
            "yemen", "ye"
        ))

        mapEntries(REGION_EUROPE, listOf(
            "albania", "al",
            "andorra", "ad",
            "austria", "at",
            "belarus", "by",
            "belgium", "be",
            "bosnia and herzegovina", "ba",
            "bulgaria", "bg",
            "croatia", "hr",
            "czech republic", "cz", "czechia",
            "denmark", "dk",
            "estonia", "ee",
            "finland", "fi",
            "france", "fr", "french",
            "germany", "de", "german",
            "greece", "gr",
            "hungary", "hu",
            "iceland", "is",
            "ireland", "ie", "irish",
            "italy", "it", "italian",
            "latvia", "lv",
            "liechtenstein", "li",
            "lithuania", "lt",
            "luxembourg", "lu",
            "malta", "mt",
            "moldova", "md",
            "monaco", "mc",
            "montenegro", "me",
            "netherlands", "nl", "dutch",
            "north macedonia", "mk", "macedonia",
            "norway", "no", "norwegian",
            "poland", "pl", "polish",
            "portugal", "pt",
            "romania", "ro",
            "russia", "ru", "russian federation",
            "san marino", "sm",
            "serbia", "rs",
            "slovakia", "sk",
            "slovenia", "si",
            "spain", "es", "spanish",
            "sweden", "se", "swedish",
            "switzerland", "ch",
            "ukraine", "ua",
            "united kingdom", "gb", "uk", "great britain", "british", "england", "scotland", "wales",
            "vatican city", "va", "holy see"
        ))

        mapEntries(REGION_NORTH_AMERICA, listOf(
            "united states", "us", "usa", "u.s.", "u.s.a.", "american",
            "canada", "ca", "canadian",
            "mexico", "mx",
            "guatemala", "gt",
            "belize", "bz",
            "honduras", "hn",
            "el salvador", "sv",
            "nicaragua", "ni",
            "costa rica", "cr",
            "panama", "pa",
            "cuba", "cu",
            "jamaica", "jm",
            "haiti", "ht",
            "dominican republic", "do",
            "puerto rico", "pr",
            "trinidad and tobago", "tt",
            "barbados", "bb",
            "bahamas", "bs",
            "greenland", "gl"
        ))

        mapEntries(REGION_SOUTH_AMERICA, listOf(
            "brazil", "br", "brazilian",
            "argentina", "ar",
            "chile", "cl",
            "colombia", "co",
            "peru", "pe",
            "venezuela", "ve",
            "ecuador", "ec",
            "bolivia", "bo",
            "paraguay", "py",
            "uruguay", "uy",
            "guyana", "gy",
            "suriname", "sr",
            "french guiana", "gf"
        ))

        mapEntries(REGION_AFRICA, listOf(
            "algeria", "dz",
            "angola", "ao",
            "benin", "bj",
            "botswana", "bw",
            "burkina faso", "bf",
            "burundi", "bi",
            "cameroon", "cm",
            "cape verde", "cv",
            "central african republic", "cf",
            "chad", "td",
            "comoros", "km",
            "congo", "cg", "republic of the congo",
            "democratic republic of the congo", "cd", "drc",
            "djibouti", "dj",
            "egypt", "eg",
            "equatorial guinea", "gq",
            "eritrea", "er",
            "eswatini", "sz", "swaziland",
            "ethiopia", "et",
            "gabon", "ga",
            "gambia", "gm",
            "ghana", "gh",
            "guinea", "gn",
            "guinea-bissau", "gw",
            "ivory coast", "ci", "côte d'ivoire", "cote d'ivoire",
            "kenya", "ke",
            "lesotho", "ls",
            "liberia", "lr",
            "libya", "ly",
            "madagascar", "mg",
            "malawi", "mw",
            "mali", "ml",
            "mauritania", "mr",
            "mauritius", "mu",
            "morocco", "ma",
            "mozambique", "mz",
            "namibia", "na",
            "niger", "ne",
            "nigeria", "ng",
            "rwanda", "rw",
            "senegal", "sn",
            "seychelles", "sc",
            "sierra leone", "sl",
            "somalia", "so",
            "south africa", "za",
            "south sudan", "ss",
            "sudan", "sd",
            "tanzania", "tz",
            "togo", "tg",
            "tunisia", "tn",
            "uganda", "ug",
            "zambia", "zm",
            "zimbabwe", "zw"
        ))

        mapEntries(REGION_OCEANIA, listOf(
            "australia", "au", "australian",
            "new zealand", "nz",
            "fiji", "fj",
            "papua new guinea", "pg",
            "samoa", "ws",
            "tonga", "to",
            "vanuatu", "vu",
            "solomon islands", "sb",
            "micronesia", "fm",
            "palau", "pw",
            "marshall islands", "mh",
            "kiribati", "ki",
            "nauru", "nr",
            "tuvalu", "tv"
        ))
    }

    private val REGION_TO_KEYS: Map<String, Set<String>> =
        MAPPING.entries.groupBy({ it.value }, { it.key })
            .mapValues { (_, keys) -> keys.toSet() }

    fun toRegion(countryOrNationality: String?): String {
        val normalized = countryOrNationality
            ?.lowercase(Locale.ROOT)
            ?.trim()
            .orEmpty()
        if (normalized.isBlank()) return REGION_OTHER
        return MAPPING[normalized] ?: REGION_OTHER
    }

    fun countriesForRegion(region: String): Set<String> {
        if (region == REGION_OTHER) return emptySet()
        val keys = REGION_TO_KEYS[region] ?: return emptySet()
        return keys.flatMap { key -> esTermVariants(key) }.toSet()
    }

    fun allKnownEsTermValues(): Set<String> =
        MAPPING.keys.flatMap { esTermVariants(it) }.toSet()

    fun allRegions(): List<String> = REGION_ORDER

    private fun esTermVariants(key: String): List<String> {
        val variants = linkedSetOf(key)
        if (key.length == 2) {
            variants.add(key.uppercase(Locale.ROOT))
        }
        if (key.contains(' ')) {
            variants.add(
                key.split(' ').joinToString(" ") { word ->
                    word.replaceFirstChar { ch ->
                        if (ch.isLowerCase()) ch.titlecase(Locale.ROOT) else ch.toString()
                    }
                }
            )
        } else if (key.length > 2) {
            variants.add(key.replaceFirstChar { ch ->
                if (ch.isLowerCase()) ch.titlecase(Locale.ROOT) else ch.toString()
            })
        }
        return variants.toList()
    }
}
