package com.herve.data.Workload.Container;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

public class Component {
    private String id;
    private String name;
    private String version;
    private String installationPath;

    public Component(JsonObject jComponent) {

        setId((String)this.map(jComponent, "id", "String"));
        setName((String)this.map(jComponent, "name", "String"));
        setVersion((String)this.map(jComponent, "version", "String"));
        setInstallationPath((String)this.map(jComponent, "installationPath", "String"));
    }
    
    Object map(JsonObject jobj, String name, String type) {
        JsonElement jsonElement = jobj.get(name);
        if ( jsonElement instanceof JsonNull || jsonElement == null ) {
            return null;
        } else {
            if ( type == "String" ) {
                return jsonElement.getAsString();
            } else if ( type == "int" ) {
                return jsonElement.getAsInt();
            } else if ( type == "Boolean" ) {
                return jsonElement.getAsBoolean();
            } else if ( type == "JsonArray") {
                return jsonElement.getAsJsonArray();
            }
        }
        return null;
    }
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getInstallationPath() {
        return installationPath;
    }

    public void setInstallationPath(String installationPath) {
        this.installationPath = installationPath;
    }



}
