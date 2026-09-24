package com.topo.core.domain.repository

import java.io.File

/**
 * Stockage des photos de blocs dans l'espace prive de l'application.
 *
 * Aucune image ne sort de l'application et aucune ne depend d'une permission
 * externe : le projet est hors-ligne par construction, et une `content://` obtenue
 * de la galerie peut voir son autorisation revoquee a tout moment. Les images
 * importees sont donc COPIEES.
 *
 * La base ne stocke que des chemins RELATIFS a ce repertoire ([File.name]), jamais
 * de chemin absolu : celui-ci change lors d'une restauration ou d'un transfert
 * d'appareil.
 */
interface BlocImageStore {

    /**
     * Cree un fichier vide destine a recevoir une capture.
     *
     * CameraX ecrit directement dans un [File] : lui fournir la destination evite
     * un buffer intermediaire en memoire pour une photo pleine resolution.
     */
    suspend fun createCaptureTarget(): File

    /** Chemin relatif a enregistrer en base pour un fichier de ce magasin. */
    fun relativePathOf(file: File): String

    /** Resout un chemin relatif en fichier lisible. */
    fun resolve(relativePath: String): File

    /**
     * Importe une image externe en la copiant dans le stockage interne.
     *
     * @param source flux de l'image d'origine, ferme par l'appelant.
     * @return le chemin relatif du fichier cree.
     */
    suspend fun import(source: () -> java.io.InputStream): String

    /** Supprime le fichier. Sans effet s'il n'existe plus. */
    suspend fun delete(relativePath: String)

    /**
     * Dimensions de l'image sans la decoder entierement.
     *
     * Une photo de telephone depasse couramment 4000x3000 en ARGB_8888, soit ~48 Mo :
     * la decoder juste pour connaitre sa taille serait un candidat direct a
     * l'OutOfMemoryError.
     */
    suspend fun readDimensions(relativePath: String): ImageDimensions

    data class ImageDimensions(val width: Int, val height: Int)
}
