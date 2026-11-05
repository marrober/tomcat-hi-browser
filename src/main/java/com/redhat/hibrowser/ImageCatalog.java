package com.redhat.hibrowser;

import java.util.List;

interface ImageCatalog {

    List<ImageTag> listTags();

    ImageReport reportFor(ImageTag tag);
}
