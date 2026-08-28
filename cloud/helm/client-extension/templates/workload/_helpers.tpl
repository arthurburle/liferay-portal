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
{{- $rootOverlay := .root.Values.overlay | default dict -}}
{{- $overlayBucket := $rootOverlay.bucket | default "" -}}
{{- $defaultOverlayVersion := $rootOverlay.version | default "master" -}}
{{- $extOverlayVersion := $overlay.version | default $defaultOverlayVersion -}}
{{- $main := .root.Values.main | default dict -}}
{{- $waitCfg := $main.waitForLiferay | default dict -}}
{{- $waitEnabled := dig "enabled" false $waitCfg -}}
{{- $waitEndpoint := dig "endpoint" "/c/portal/robots" $waitCfg -}}
{{- $waitInterval := dig "pollIntervalSeconds" 5 $waitCfg -}}
{{- $dxp := .root.Values.dxp | default dict -}}
{{- $liferayProtocol := dig "protocol" "https" $dxp -}}
{{- $liferayDomain := dig "mainDomain" "" $dxp -}}
{{- $waitImage := dig "image" (dict "repository" "busybox" "tag" "1.36" "pullPolicy" "IfNotPresent") $waitCfg -}}
metadata:
    labels:
        {{- include "extLabels" . | nindent 8 }}
spec:
    {{- if or $overlayEnabled $waitEnabled }}
    {{- $initScriptsPath := include "initScriptsPath" $root }}
    initContainers:
        {{- if $overlayEnabled }}
        -   command:
                -   sh
                -   -c
                -   |
                    set -o errexit
                    {{- range $overlay.copy }}
                    {{- $ver := .version | default $extOverlayVersion }}
                    sh {{ $initScriptsPath }}/overlay-sync.sh "gcs" {{ printf "%s/%s" $ver .from | quote }} {{ .into | quote }}
                    {{- end }}
            env:
                -   name: LIFERAY_INIT_SCRIPTS_PATH
                    value: {{ $initScriptsPath | quote }}
                -   name: LIFERAY_OVERLAY_BUCKET_NAME
                    value: {{ $overlayBucket | quote }}
            image: {{ printf "%s:%s" (dig "image" "repository" "rclone/rclone" $overlay) (dig "image" "tag" "1.66" $overlay | toString) }}
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
                -   mountPath: {{ $initScriptsPath }}
                    name: client-extension-init-scripts
                -   mountPath: /temp
                    name: overlay-staging
        {{- end }}
        {{- if $waitEnabled }}
        -   command:
                -   sh
                -   {{ $initScriptsPath }}/wait-for-liferay.sh
            env:
                -   name: LIFERAY_INIT_SCRIPTS_PATH
                    value: {{ $initScriptsPath | quote }}
                -   name: LIFERAY_URL
                    value: {{ printf "%s://%s%s" $liferayProtocol $liferayDomain $waitEndpoint | quote }}
                -   name: POLL_INTERVAL_SECONDS
                    value: {{ $waitInterval | quote }}
            image: {{ printf "%s:%s" $waitImage.repository ($waitImage.tag | toString) }}
            imagePullPolicy: {{ dig "pullPolicy" "IfNotPresent" $waitImage }}
            name: wait-for-liferay
            resources:
                limits:
                    cpu: 100m
                    memory: 64Mi
                requests:
                    cpu: 10m
                    memory: 16Mi
            securityContext:
                allowPrivilegeEscalation: false
                capabilities:
                    drop:
                        -   ALL
                runAsNonRoot: true
                runAsUser: 1000
                seccompProfile:
                    type: RuntimeDefault
            volumeMounts:
                -   mountPath: {{ $initScriptsPath }}
                    name: client-extension-init-scripts
        {{- end }}
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
            {{- $secrets := dig "secrets" list $ext -}}
            {{- $extraEnvFrom := dig "envFrom" list $ext -}}
            {{- if or $secrets $extraEnvFrom }}
            envFrom:
                {{- range $s := $secrets }}
                -   secretRef:
                        name: {{ $s | quote }}
                {{- end }}
                {{- with $extraEnvFrom }}
                {{- toYaml . | nindent 16 }}
                {{- end }}
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
            {{- with (dig "livenessProbe" dict $ext) }}
            livenessProbe:
                {{- toYaml . | nindent 16 }}
            {{- end }}
            {{- with (dig "readinessProbe" dict $ext) }}
            readinessProbe:
                {{- toYaml . | nindent 16 }}
            {{- end }}
            {{- with (dig "startupProbe" dict $ext) }}
            startupProbe:
                {{- toYaml . | nindent 16 }}
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
                -   mountPath: {{ required "extensions[].overlay.copy[].mountPath is required" .mountPath | quote }}
                    name: overlay-staging
                    subPath: {{ .into | quote }}
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
        {{- if or $overlayEnabled $waitEnabled }}
        -   configMap:
                defaultMode: 493
                name: client-extension-init-scripts
            name: client-extension-init-scripts
        {{- end }}
        -   configMap:
                name: {{ printf "%s-lxc-dxp-metadata" (required "dxp.mainDomain is required" $root.Values.dxp.mainDomain) }}
                optional: true
            name: dxp-metadata
        -   configMap:
                name: {{ printf "%s-%s-lxc-ext-init-metadata" $ext.name (required "dxp.mainDomain is required" $root.Values.dxp.mainDomain) }}
                optional: true
            name: ext-init-metadata
        {{- if $overlayEnabled }}
        -   emptyDir: {}
            name: overlay-staging
        {{- end }}
{{- end -}}