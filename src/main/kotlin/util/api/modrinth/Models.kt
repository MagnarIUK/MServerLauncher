package com.magnariuk.util.api.modrinth
import java.time.Instant

data class DonationUrl(
    val id: String? = null,
    val platform: String? = null,
    val url: String? = null
)

data class License(
    val id: String? = null,
    val name: String? = null,
    val url: String? = null
)

data class GalleryItem(
    val url: String,
    val featured: Boolean,
    val title: String? = null,
    val description: String? = null,
    val created: Instant,
    val ordering: Int? = null
)

data class Project(
    val id: String,
    val slug: String,
    val title: String? = null,
    val description: String? = null,
    val categories: List<String>? = null,
    val clientSide: String? = null,
    val serverSide: String? = null,
    val body: String? = null,
    val status: String? = null,
    val requestedStatus: String? = null,
    val additionalCategories: List<String>? = null,
    val issuesUrl: String? = null,
    val sourceUrl: String? = null,
    val wikiUrl: String? = null,
    val discordUrl: String? = null,
    val donationUrls: List<DonationUrl>? = null,
    val projectType: String? = null,
    val downloads: Int? = null,
    val iconUrl: String? = null,
    val color: Int? = null,
    val threadId: String? = null,
    val monetizationStatus: String? = null,
    val team: String,
    val bodyUrl: String? = null,
    val published: Instant,
    val updated: Instant,
    val approved: Instant? = null,
    val queued: Instant? = null,
    val followers: Int,
    val license: License? = null,
    val versions: List<String>? = null,
    val gameVersions: List<String>? = null,
    val loaders: List<String>? = null,
    val gallery: List<GalleryItem>? = null
)

data class Dependencies(
    val versionId: String? = null,
    val projectId: String? = null,
    val fileName: String? = null,
    val dependencyType: String
)

data class Hashes(
    val sha512: String,
    val sha1: String
)

data class File(
    val hashes: Hashes,
    val url: String,
    val filename: String,
    val primary: Boolean,
    val size: Int,
    val fileType: String? = null
)

data class Version(
    val id: String,
    val projectId: String,
    val slug: String? = null,
    val name: String? = null,
    val versionNumber: String? = null,
    val changelog: String? = null,
    val dependencies: List<Dependencies>? = null,
    val gameVersions: List<String>? = null,
    val versionType: String? = null,
    val loaders: List<String>? = null,
    val featured: Boolean? = null,
    val status: String? = null,
    val requestedStatus: String? = null,
    val authorId: String,
    val datePublished: Instant,
    val downloads: Int,
    val changelogUrl: String? = null, // Deprecated
    val files: List<File>
)

data class Hit(
    val projectId: String,
    val author: String,
    val versions: List<String>,
    val follows: Int,
    val dateCreated: Instant,
    val dateModified: Instant,
    val projectType: String? = null,
    val slug: String? = null,
    val title: String? = null,
    val description: String? = null,
    val categories: List<String>? = null,
    val displayCategories: List<String>? = null,
    val downloads: Int? = null,
    val iconUrl: String? = null,
    val latestVersion: String? = null,
    val license: String? = null,
    val clientSide: String? = null,
    val serverSide: String? = null,
    val gallery: List<String>? = null,
    val featuredGallery: String? = null,
    val color: Int? = null
)

data class SearchResult(
    val hits: List<Hit>,
    val offset: Int,
    val limit: Int,
    val totalHits: Int
)
