/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-18 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.autopsy.imagegallery.datamodel;

import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.events.ContentTagAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.ContentTagDeletedEvent;
import org.sleuthkit.autopsy.casemodule.services.TagsManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.DhsImageCategory;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Manages Tags, Tagging, and the relationship between Categories and Tags in
 * the autopsy Db. Delegates some work to the backing autopsy TagsManager.
 */
@NbBundle.Messages({"DrawableTagsManager.followUp=Follow Up",
    "DrawableTagsManager.bookMark=Bookmark"})
public final class DrawableTagsManager {

    private static final Logger logger = Logger.getLogger(DrawableTagsManager.class.getName());

    private static final Image FOLLOW_UP_IMAGE = new Image("/org/sleuthkit/autopsy/imagegallery/images/flag_red.png");
    private static final Image BOOKMARK_IMAGE = new Image("/org/sleuthkit/autopsy/images/star-bookmark-icon-16.png");

    private final TagsManager autopsyTagsManager;
    /** The tag name corresponding to the "built-in" tag "Follow Up" */
    private final TagName followUpTagName;
    private final TagName bookmarkTagName;

    /**
     * Used to distribute TagsChangeEvents
     */
    private final EventBus tagsEventBus
            = new AsyncEventBus(
                    Executors.newSingleThreadExecutor(
                            new BasicThreadFactory.Builder()
                                    .namingPattern("Tags Event Bus")//NON-NLS
                                    .uncaughtExceptionHandler((Thread thread, Throwable throwable)
                                            -> logger.log(Level.SEVERE, "Uncaught exception in DrawableTagsManager event bus handler.", throwable)) //NON-NLS
                                    .build()));

    public DrawableTagsManager(ImageGalleryController controller) throws TskCoreException {
        this.autopsyTagsManager = controller.getAutopsyCase().getServices().getTagsManager();
        followUpTagName = getTagName(Bundle.DrawableTagsManager_followUp());
        bookmarkTagName = getTagName(Bundle.DrawableTagsManager_bookMark());
    }

    /**
     * register an object to receive CategoryChangeEvents
     *
     * @param listener
     */
    public void registerListener(Object listener) {
        tagsEventBus.register(listener);
    }

    /**
     * unregister an object from receiving CategoryChangeEvents
     *
     * @param listener
     */
    public void unregisterListener(Object listener) {
        tagsEventBus.unregister(listener);
    }

    public void fireTagAddedEvent(ContentTagAddedEvent event) {
        tagsEventBus.post(event);
    }

    public void fireTagDeletedEvent(ContentTagDeletedEvent event) {
        tagsEventBus.post(event);
    }

    /**
     * Get the follow up TagName.
     *
     * @return The follow up TagName.
     */
    public TagName getFollowUpTagName() {
        return followUpTagName;
    }

    /**
     * Get the bookmark TagName.
     *
     * @return The bookmark TagName.
     */
    private TagName getBookmarkTagName() throws TskCoreException {
        return bookmarkTagName;
    }

    /**
     * Get all the TagNames that are not categories
     *
     * @return All the TagNames that are not categories, in alphabetical order
     *         by displayName.
     *
     * @throws org.sleuthkit.datamodel.TskCoreException
     */
    public List<TagName> getNonCategoryTagNames() throws TskCoreException {
        return autopsyTagsManager.getAllTagNames().stream()
                .filter(CategoryManager::isNotCategoryTagName)
                .distinct().sorted()
                .collect(Collectors.toList());
    }

    /**
     * Get all the TagNames that are categories
     *
     * @return All the TagNames that are categories, in alphabetical order by
     *         displayName.
     *
     * @throws org.sleuthkit.datamodel.TskCoreException
     */
    public List<TagName> getCategoryTagNames() throws TskCoreException {
        return autopsyTagsManager.getAllTagNames().stream()
                .filter(CategoryManager::isCategoryTagName)
                .distinct().sorted()
                .collect(Collectors.toList());
    }

    /**
     * Gets content tags by content.
     *
     * @param content The content of interest.
     *
     * @return A list, possibly empty, of the tags that have been applied to the
     *         content.
     *
     * @throws TskCoreException if there was an error reading from the db
     */
    public List<ContentTag> getContentTags(Content content) throws TskCoreException {
        return autopsyTagsManager.getContentTagsByContent(content);
    }

    /**
     * Gets content tags by DrawableFile.
     *
     * @param drawable The DrawableFile of interest.
     *
     * @return A list, possibly empty, of the tags that have been applied to the
     *         DrawableFile.
     *
     * @throws TskCoreException if there was an error reading from the db
     */
    public List<ContentTag> getContentTags(DrawableFile drawable) throws TskCoreException {
        return getContentTags(drawable.getAbstractFile());
    }

    public TagName getTagName(String displayName) throws TskCoreException {

        TagName returnTagName = autopsyTagsManager.getDisplayNamesToTagNamesMap().get(displayName);
        if (returnTagName != null) {
            return returnTagName;
        }
        try {
            return autopsyTagsManager.addTagName(displayName);
        } catch (TagsManager.TagNameAlreadyExistsException ex) {
            returnTagName = autopsyTagsManager.getDisplayNamesToTagNamesMap().get(displayName);
            if (returnTagName != null) {
                return returnTagName;
            }
            throw new TskCoreException("Tag name exists but an error occured in retrieving it", ex);
        }
    }

    public TagName getTagName(DhsImageCategory cat) throws TskCoreException {
        return getTagName(cat.getDisplayName());
    }

    public ContentTag addContentTag(DrawableFile file, TagName tagName, String comment) throws TskCoreException {
        return autopsyTagsManager.addContentTag(file.getAbstractFile(), tagName, comment);
    }

    public List<ContentTag> getContentTagsByTagName(TagName tagName) throws TskCoreException {
        return autopsyTagsManager.getContentTagsByTagName(tagName);
    }

    public List<TagName> getAllTagNames() throws TskCoreException {
        return autopsyTagsManager.getAllTagNames();
    }

    public List<TagName> getTagNamesInUse() throws TskCoreException {
        return autopsyTagsManager.getTagNamesInUse();
    }

    public void deleteContentTag(ContentTag contentTag) throws TskCoreException {
        autopsyTagsManager.deleteContentTag(contentTag);
    }

    public Node getGraphic(TagName tagname) {
        try {
            if (tagname.equals(getFollowUpTagName())) {
                return new ImageView(FOLLOW_UP_IMAGE);
            } else if (tagname.equals(getBookmarkTagName())) {
                return new ImageView(BOOKMARK_IMAGE);
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Failed to get \"Follow Up\" or \"Bookmark\"tag name from db.", ex);
        }
        return DrawableAttribute.TAGS.getGraphicForValue(tagname);
    }
}
