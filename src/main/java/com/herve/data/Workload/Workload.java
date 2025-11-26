package com.herve.data.Workload;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.herve.data.CustomColumn.CustomColumns;
import com.herve.data.Workload.Container.Containers;

public class Workload {
    private int id;
    private String kind;
    private String name;
    private String namespace;
    private String clusterId;
    private String clusterName;
    private String replicas;
    private String chargebackGroupName;
    private Containers containers;
    private String annotations;
    private String charged;
    

    private CustomColumns customColumns;

    public Workload(JsonObject jWorkload) {
        setId((int) this.map(jWorkload, "id", "int"));
        setKind((String) this.map(jWorkload, "kind", "String"));
        setName((String) this.map(jWorkload, "name", "String"));
        setNamespace((String) this.map(jWorkload, "namespace", "String"));
        setClusterId((String) this.map(jWorkload, "clusterId", "String"));
        setClusterName((String) this.map(jWorkload, "clusterName", "String"));
        setChargebackGroupName((String) this.map(jWorkload, "chargedbackGroupName", "String"));
        setReplicas((String) this.map(jWorkload, "replicas", "String"));
        setContainers((JsonArray) this.map(jWorkload, "containers", "JsonArray"));
        setAnnotations((String) this.map(jWorkload, "annotations", "String"));
        setCustomColumns((JsonArray) this.map(jWorkload, "customColumns", "JsonArray"));
    }

    Object map(JsonObject jobj, String name, String type) {
        JsonElement jsonElement = jobj.get(name);
        if (jsonElement instanceof JsonNull || jsonElement == null) {
            return null;
        } else {
            if (type == "String") {
                return jsonElement.getAsString();
            } else if (type == "int") {
                return jsonElement.getAsInt();
            } else if (type == "Boolean") {
                return jsonElement.getAsBoolean();
            } else if (type == "JsonArray") {
                return jsonElement.getAsJsonArray();
            }
        }
        return null;
    }

    public CustomColumns getCustomColumns() {
        return this.customColumns;
    }

    public void setCustomColumns(JsonArray jaCustomColumns) {
        CustomColumns customColumns = new CustomColumns(jaCustomColumns);
        this.customColumns = customColumns;
    }

    public String getReplicas() {
        return replicas;
    }

    public void setReplicas(String replicas) {
        this.replicas = replicas;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getClusterId() {
        return clusterId;
    }

    public void setClusterId(String clusterId) {
        this.clusterId = clusterId;
    }

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public String getChargebackGroupName() {
        return chargebackGroupName;
    }

    public void setChargebackGroupName(String chargebackGroupName) {
        this.chargebackGroupName = chargebackGroupName;
    }

    public Containers getContainers() {
        return containers;
    }

    public String getCharged() {
        return charged;
    }

    public void setCharged(String charged) {
        this.charged = charged;
    }

    public void setContainers(JsonArray jaContainers) {
        this.containers = new Containers(jaContainers);
        
    }

    public String getAnnotations() {
        return annotations;
    }

    public void setAnnotations(String annotations) {
        this.annotations = annotations;
    }

}
