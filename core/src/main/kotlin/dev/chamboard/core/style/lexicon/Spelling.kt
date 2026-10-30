package dev.chamboard.core.style.lexicon

/**
 * STY-3: spelling variants and common misspellings mapped to one canonical
 * (US) form. Typos and regional spelling are among the most identifying
 * stylometric features. Keys are lowercase; case is re-applied by the rule.
 *
 * Review rule for entries: the key must not itself be a common correct English
 * word (e.g. "cant", "wont", "ill", "its", "were" are excluded).
 */
object Spelling {

    /** -ise/-yse stems whose inflections all map to -ize/-yze. */
    private val iseStems = listOf(
        "organis", "realis", "recognis", "apologis", "criticis", "emphasis", "prioritis", "summaris",
        "specialis", "finalis", "minimis", "maximis", "utilis", "customis", "optimis", "categoris",
        "memoris", "sympathis", "authoris", "familiaris", "visualis", "standardis", "normalis",
        "personalis", "initialis", "synchronis", "legalis", "stabilis", "modernis", "mobilis",
        "characteris", "generalis", "hospitalis", "centralis", "localis", "globalis",
    )

    private val yseStems = listOf("analys", "paralys", "catalys")

    private fun iseForms(stem: String): Map<String, String> {
        val us = stem.dropLast(1) + "z"
        return mapOf(
            "${stem}e" to "${us}e",
            "${stem}ed" to "${us}ed",
            "${stem}es" to "${us}es",
            "${stem}ing" to "${us}ing",
            "${stem}ation" to "${us}ation",
            "${stem}ations" to "${us}ations",
        )
    }

    val ukToUs: Map<String, String> = buildMap {
        iseStems.forEach { putAll(iseForms(it)) }
        yseStems.forEach { stem ->
            val us = stem.dropLast(1) + "z"
            put("${stem}e", "${us}e")
            put("${stem}ed", "${us}ed")
            put("${stem}ing", "${us}ing")
        }
        // Words like "analyses" are also a plural noun of "analysis"; leave them.
        listOf(
            "colour", "favour", "honour", "humour", "labour", "neighbour", "behaviour", "flavour",
            "harbour", "rumour", "vapour", "savour", "endeavour", "armour", "odour", "parlour", "vigour",
        ).forEach { w ->
            val us = w.replace("our", "or")
            put(w, us)
            put(w + "s", us + "s")
            put(w + "ed", us + "ed")
            put(w + "ing", us + "ing")
        }
        putAll(
            mapOf(
                "colourful" to "colorful", "favourite" to "favorite", "favourites" to "favorites",
                "favourable" to "favorable", "honourable" to "honorable", "neighbourhood" to "neighborhood",
                "neighbourhoods" to "neighborhoods", "behavioural" to "behavioral", "humourous" to "humorous",
                "centre" to "center", "centres" to "centers", "centred" to "centered", "theatre" to "theater",
                "theatres" to "theaters", "metre" to "meter", "metres" to "meters", "litre" to "liter",
                "litres" to "liters", "fibre" to "fiber", "calibre" to "caliber", "sombre" to "somber",
                "travelled" to "traveled", "travelling" to "traveling", "traveller" to "traveler",
                "travellers" to "travelers", "cancelled" to "canceled", "cancelling" to "canceling",
                "labelled" to "labeled", "labelling" to "labeling", "modelled" to "modeled",
                "modelling" to "modeling", "fuelled" to "fueled", "levelled" to "leveled",
                "counselling" to "counseling", "jewellery" to "jewelry", "grey" to "gray", "greys" to "grays",
                "whilst" to "while", "amongst" to "among", "learnt" to "learned", "spelt" to "spelled",
                "dreamt" to "dreamed", "programme" to "program", "programmes" to "programs",
                "catalogue" to "catalog", "analogue" to "analog", "defence" to "defense",
                "offence" to "offense", "licence" to "license", "tyre" to "tire", "tyres" to "tires",
                "aluminium" to "aluminum", "maths" to "math", "plough" to "plow", "sceptical" to "skeptical",
                "ageing" to "aging", "manoeuvre" to "maneuver", "paediatric" to "pediatric",
                "encyclopaedia" to "encyclopedia", "enrol" to "enroll", "fulfil" to "fulfill",
                "skilful" to "skillful", "cheque" to "check", "cheques" to "checks", "mum" to "mom",
                "mums" to "moms", "judgement" to "judgment", "acknowledgement" to "acknowledgment",
                "practise" to "practice", "practised" to "practiced", "practising" to "practicing",
            ),
        )
    }

    private val misspellings: Map<String, String> = mapOf(
        "teh" to "the", "recieve" to "receive", "recieved" to "received", "beleive" to "believe",
        "belive" to "believe", "definately" to "definitely", "definatly" to "definitely",
        "definetly" to "definitely", "seperate" to "separate", "seperately" to "separately",
        "occured" to "occurred", "occurence" to "occurrence", "untill" to "until", "wierd" to "weird",
        "thier" to "their", "becuase" to "because", "becasue" to "because", "beacuse" to "because",
        "goverment" to "government", "tommorow" to "tomorrow", "tommorrow" to "tomorrow",
        "tomorow" to "tomorrow", "accomodate" to "accommodate", "arguement" to "argument",
        "begining" to "beginning", "calender" to "calendar", "commited" to "committed",
        "concious" to "conscious", "embarass" to "embarrass", "enviroment" to "environment",
        "existance" to "existence", "familar" to "familiar", "finaly" to "finally", "foriegn" to "foreign",
        "freind" to "friend", "freinds" to "friends", "grammer" to "grammar", "happend" to "happened",
        "harrass" to "harass", "immediatly" to "immediately", "independant" to "independent",
        "knowlege" to "knowledge", "liason" to "liaison", "libary" to "library",
        "maintainance" to "maintenance", "maintenence" to "maintenance", "millenium" to "millennium",
        "neccessary" to "necessary", "necesary" to "necessary", "noticable" to "noticeable",
        "occassion" to "occasion", "occassionally" to "occasionally", "persistant" to "persistent",
        "posession" to "possession", "prefered" to "preferred", "publically" to "publicly",
        "realy" to "really", "reccomend" to "recommend", "recomend" to "recommend",
        "refered" to "referred", "relevent" to "relevant", "religous" to "religious",
        "remeber" to "remember", "resistence" to "resistance", "responsability" to "responsibility",
        "rythm" to "rhythm", "sieze" to "seize", "similiar" to "similar", "sincerly" to "sincerely",
        "speach" to "speech", "succesful" to "successful", "successfull" to "successful",
        "supercede" to "supersede", "suprise" to "surprise", "suprised" to "surprised", "tatoo" to "tattoo",
        "truely" to "truly", "unfortunatly" to "unfortunately", "alot" to "a lot", "aswell" to "as well",
        "infact" to "in fact", "incase" to "in case", "eachother" to "each other", "noone" to "no one",
        "alright" to "all right", "wich" to "which", "whith" to "with", "thats" to "that's",
        "whats" to "what's", "theres" to "there's", "dont" to "don't", "doesnt" to "doesn't",
        "didnt" to "didn't", "isnt" to "isn't", "arent" to "aren't", "wasnt" to "wasn't",
        "werent" to "weren't", "havent" to "haven't", "hasnt" to "hasn't", "hadnt" to "hadn't",
        "wouldnt" to "wouldn't", "shouldnt" to "shouldn't", "couldnt" to "couldn't", "mustnt" to "mustn't",
        "im" to "I'm", "ive" to "I've", "youre" to "you're", "theyre" to "they're", "youve" to "you've",
        "weve" to "we've", "theyve" to "they've", "youll" to "you'll", "theyll" to "they'll",
        "shes" to "she's",
    )

    /** Canonical (US) form for every regional variant and typo. */
    val all: Map<String, String> = ukToUs + misspellings

    /**
     * US -> UK for UK-profile masks (STY-9). Excludes targets that are also
     * correct UK words in some senses ("check", "program", "license", "practice",
     * "meter", "tire") or that UK writers use too ("while", "among", "learned").
     */
    val usToUk: Map<String, String> = run {
        val ambiguous = setOf(
            "check", "checks", "program", "programs", "license", "practice", "meter", "meters", "tire", "tires",
            "while", "among", "learned", "spelled", "dreamed", "mom", "moms",
        )
        ukToUs.filterValues { it !in ambiguous }.entries.associate { (uk, us) -> us to uk }
    }
}
