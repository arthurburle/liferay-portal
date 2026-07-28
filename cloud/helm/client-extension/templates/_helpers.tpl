{{- /*
    Google Cloud L7 LB health-check source ranges. Constants published by
    Google — https://cloud.google.com/load-balancing/docs/health-check-concepts#ip-ranges
*/ -}}
{{- define "gkeLoadBalancerSourceRanges" -}}
-   ipBlock:
        cidr: 130.211.0.0/22
-   ipBlock:
        cidr: 35.191.0.0/16
{{- end }}

{{- /* Labels applied to every resource created by this chart. */ -}}
{{- define "commonLabels" -}}
environmentId: {{ .Values.environmentId }}
projectId: {{ .Values.projectId }}
workload_class: custom
{{- end -}}

{{- /*
Labels for a per-extension resource. Context: dict with "root" and "ext".
*/ -}}
{{- define "extLabels" -}}
app: {{ .ext.name }}
component: client-extension
{{ include "commonLabels" .root }}
{{- end -}}

{{- /* Immutable subset for pod selector. Context: dict "root" "ext". */ -}}
{{- define "extSelectorLabels" -}}
app: {{ .ext.name }}
component: client-extension
{{- end -}}

{{- /*
Pod template shared by Deployment, Job, and CronJob workload kinds.
Context: dict with "root" and "ext". Callers wrap and nindent.
*/ -}}
{{- define "podTemplate" -}}
{{- $root := .root -}}
{{- $ext := .ext -}}
{{- $kind := dig "kind" "Deployment" $ext -}}
{{- $overlay := dig "overlay" dict $ext -}}
{{- $overlayEnabled := and (dig "enabled" false $overlay) (dig "copy" list $overlay) -}}
metadata:
    labels:
        {{- include "extLabels" . | nindent 8 }}
spec:
    {{- if $overlayEnabled }}
    initContainers:
        -   command:
                -   sh
                -   -c
                -   |
                    set -o errexit
                    {{- range $overlay.copy }}
                    gcloud storage cp {{ .from | quote }} {{ printf "/overlay-staging/%s" (base .into) | quote }}
                    {{- end }}
            image: {{ printf "%s:%s" (dig "image" "repository" "google/cloud-sdk" $overlay) (dig "image" "tag" "alpine" $overlay | toString) }}
            imagePullPolicy: {{ dig "image" "pullPolicy" "IfNotPresent" $overlay }}
            name: overlay
            resources:
                {{- toYaml (dig "resources" (dict "limits" (dict "cpu" "500m" "memory" "256Mi") "requests" (dict "cpu" "100m" "memory" "128Mi")) $overlay) | nindent 16 }}
            securityContext:
                allowPrivilegeEscalation: false
                capabilities:
                    drop:
                        -   ALL
                readOnlyRootFilesystem: false
            volumeMounts:
                -   mountPath: /overlay-staging
                    name: overlay-staging
    {{- end }}
    containers:
        -   {{- with (dig "args" list $ext) }}
            args:
                {{- toYaml . | nindent 16 }}
            {{- end }}
            {{- with (dig "command" list $ext) }}
            command:
                {{- toYaml . | nindent 16 }}
            {{- end }}
            env:
                -   name: LIFERAY_ROUTES_CLIENT_EXTENSION
                    value: /etc/liferay/lxc/ext-init-metadata
                -   name: LIFERAY_ROUTES_DXP
                    value: /etc/liferay/lxc/dxp-metadata
                {{- with (dig "env" list $ext) }}
                {{- toYaml . | nindent 16 }}
                {{- end }}
            envFrom:
                -   configMapRef:
                        name: {{ printf "%s-runtime-env" $ext.name }}
                {{- range $s := (dig "secrets" list $ext) }}
                -   secretRef:
                        name: {{ $s | quote }}
                {{- end }}
                {{- with (dig "envFrom" list $ext) }}
                {{- toYaml . | nindent 16 }}
                {{- end }}
            image: {{ printf "%s:%s" (required "extensions[].image.repository is required" $ext.image.repository) (required "extensions[].image.tag is required" ($ext.image.tag | toString)) }}
            imagePullPolicy: {{ dig "image" "pullPolicy" "Always" $ext }}
            name: {{ $ext.name }}
            {{- if eq $kind "Deployment" }}
            ports:
                -   containerPort: {{ required "extensions[].port is required for Deployment kind" $ext.port }}
                    name: http
                    protocol: TCP
            {{- end }}
            {{- with (dig "resources" dict $ext) }}
            {{- if . }}
            resources:
                {{- toYaml . | nindent 16 }}
            {{- end }}
            {{- end }}
            securityContext:
                allowPrivilegeEscalation: false
                capabilities:
                    drop:
                        -   ALL
                {{- with (dig "securityContext" dict $ext) }}
                {{- toYaml . | nindent 16 }}
                {{- end }}
            volumeMounts:
                -   mountPath: /etc/liferay/lxc/dxp-metadata
                    name: dxp-metadata
                -   mountPath: /etc/liferay/lxc/ext-init-metadata
                    name: ext-init-metadata
                {{- if $overlayEnabled }}
                {{- range $overlay.copy }}
                -   mountPath: {{ .into | quote }}
                    name: overlay-staging
                    subPath: {{ base .into | quote }}
                {{- end }}
                {{- end }}
    {{- if or (eq $kind "Job") (eq $kind "CronJob") }}
    restartPolicy: Never
    {{- end }}
    securityContext:
        runAsNonRoot: true
        runAsUser: 1000
        seccompProfile:
            type: RuntimeDefault
        {{- with (dig "podSecurityContext" dict $ext) }}
        {{- toYaml . | nindent 8 }}
        {{- end }}
    serviceAccountName: {{ include "serviceAccountName" $root }}
    volumes:
        -   configMap:
                name: {{ printf "%s-dxp-metadata" $root.Release.Name }}
            name: dxp-metadata
        -   configMap:
                name: {{ printf "%s-ext-init-metadata" $ext.name }}
            name: ext-init-metadata
        {{- if $overlayEnabled }}
        -   emptyDir: {}
            name: overlay-staging
        {{- end }}
{{- end -}}

{{- define "serviceAccountName" -}}
{{- default .Release.Name .Values.serviceAccount.name -}}
{{- end -}}

{{- /* Labels for chart-wide (shared per-ns) resources. */ -}}
{{- define "sharedLabels" -}}
component: client-extension
{{ include "commonLabels" . }}
{{- end -}}