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