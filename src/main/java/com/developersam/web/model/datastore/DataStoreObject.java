package com.developersam.web.model.datastore;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Text;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

/**
 * A superclass designed to bind closely with DataStore operations.
 * Its subclass must be a logical object related to both a DataStore entity and
 * a Java bean like object.
 */
public abstract class DataStoreObject {
    
    /**
     * Key of parent data store object, can be {@code null}.
     */
    private Key parentKey;
    /**
     * Name of the data store table.
     */
    private String dataStoreTableName;
    
    /**
     * A data store service that are used by all data store object.
     */
    private static final DatastoreService DATASTORE =
            DatastoreServiceFactory.getDatastoreService();
    /**
     * A consistently used date formatter.
     */
    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd hh:mm");
    /**
     * A calender used to find time.
     */
    private static final Calendar CALENDAR = Calendar.getInstance();
    
    static {
        // Statically initialize the time zone.
        DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("America/New_York"));
    }
    
    /**
     * The default constructor is used when dataStore does not need to be
     * initialized.
     */
    protected DataStoreObject() {}
    
    /**
     * This constructor is used when dataStore object must be initialized to
     * support db operations.
     *
     * @param dataStoreTableName DataStore table name, which specifies for the
     * entire class which kind of object to fetch.
     */
    protected DataStoreObject(String dataStoreTableName) {
        this.dataStoreTableName = dataStoreTableName;
    }
    
    /**
     * Bind a parent key to the object, so that entity generation and query can
     * be associated with its parent.
     * The method does NOT need to be called for all situations.
     *
     * @param parentKey key of parent entity.
     */
    protected void setParentKey(Key parentKey) {
        this.parentKey = parentKey;
    }
    
    /**
     * Obtain the query associated with the entity name (and parent key
     * sometimes).
     *
     * @return query object that can be further modified by filters.
     */
    protected Query getQuery() {
        if (parentKey == null) {
            return new Query(dataStoreTableName);
        } else {
            return new Query(dataStoreTableName).setAncestor(parentKey);
        }
    }
    
    /**
     * Obtain a new entity associated with the entity name  (and parent key
     * sometimes).
     *
     * @return a new entity to be modified and added to database.
     */
    protected Entity getNewEntity() {
        if (parentKey == null) {
            return new Entity(dataStoreTableName);
        } else {
            return new Entity(dataStoreTableName, parentKey);
        }
    }
    
    /**
     * A helper method to obtain an entity by a string form of key.
     *
     * @param key key in string.
     * @return entity with given key.
     */
    protected Entity getEntityByKey(String key) {
        return getEntityByKey(KeyFactory.stringToKey(key));
    }
    
    /**
     * A helper method to obtain an entity by key.
     *
     * @param key key of entity.
     * @return entity with given key.
     */
    protected Entity getEntityByKey(Key key) {
        try {
            return DATASTORE.get(key);
        } catch (EntityNotFoundException e) {
            return null;
        }
    }
    
    /**
     * A helper method to put an entity into database.
     *
     * @param entity entity to be put into database
     */
    protected void putIntoDatabase(Entity entity) {
        DATASTORE.put(entity);
    }
    
    /**
     * A helper method to remove an entity from database by its key
     *
     * @param key key of to-be-removed entity
     */
    protected void removeFromDatabase(Key key) {
        DATASTORE.delete(key);
    }
    
    /**
     * A helper method to obtain prepared query by given a final query
     *
     * @param q final query.
     * @return a prepared query ready to deliver results.
     */
    protected PreparedQuery getPreparedQuery(Query q) {
        return DATASTORE.prepare(q);
    }
    
    /**
     * A helper method to convert long text to string.
     * It returns null if the text is null.
     *
     * @param text text object from app engine DataStore.
     * @return string form of text.
     */
    private static String textToString(Text text) {
        if (text == null) {
            return null;
        } else {
            return text.getValue();
        }
    }
    
    /**
     * A helper method to convert long text to string.
     * It returns null if the text is null.
     *
     * @param o it must be text object from app engine DataStore.
     * @return string form of text.
     */
    protected static String textToString(Object o) {
        return textToString((Text) o);
    }
    
    /**
     * Format a date object to yyyy-MM-dd hh:mm in EST.
     *
     * @param date date object
     * @return a string representation of time in EST (US New York)
     */
    protected String dateFormatter(Date date) {
        CALENDAR.setTime(date);
        return DATE_FORMAT.format(CALENDAR.getTime());
    }
    
    /**
     * Format date string like yyyy-MM-dd hh:mm in EST to a date object.
     *
     * @param date string representation of the day
     * @return a date object
     * @throws ParseException error of parsing
     */
    protected Date dateFormatter(String date) throws ParseException {
        return DATE_FORMAT.parse(date);
    }
    
}