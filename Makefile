.PHONY: docker docker.all
SHELL := /bin/bash -o pipefail

ROOT_PATH := $(shell dirname $(realpath $(lastword $(MAKEFILE_LIST))))
CONTEXT ?= ${ROOT_PATH}/dist
SKIP_TEST ?= true
DOCKER_BUILD_TOP:=${ROOT_PATH}/docker_build
HUB ?= lyndonshi/net-tunnel-
SERVER_NAME ?= Server
CLIENT_NAME ?= Client
TAG ?= latest

DOCKER_TARGETS:=docker.client docker.server

ifneq ($(BASE_IMAGE),)
  BUILD_ARGS := $(BUILD_ARGS) --build-arg BASE_IMAGE=$(BASE_IMAGE)
endif

%.server: NAME = $(SERVER_NAME)
%.client: NAME = $(CLIENT_NAME)

docker.%: PLATFORMS =
docker.%: LOAD_OR_PUSH = --load
push.%: PLATFORMS = --platform linux/amd64,linux/arm64/v8,linux/arm/v7
push.%: LOAD_OR_PUSH = --push
DIST_JAR_NAME = $(wildcard ${CONTEXT}/NT-$(NAME)-*.jar)

build.docker.% push.docker.%:
	$(DOCKER_RULE)

docker.build: $(DOCKER_TARGETS:%=build.%)
docker.push: $(DOCKER_TARGETS:%=push.%)

define DOCKER_RULE
	mkdir -p $(DOCKER_BUILD_TOP)/$(NAME)
	cp -r $(DIST_JAR_NAME) $(DOCKER_BUILD_TOP)/$(NAME)
	docker buildx create --use --driver docker-container --name nt_builder > /dev/null 2>&1 || true
	docker buildx build $(PLATFORMS) $(LOAD_OR_PUSH) \
		--no-cache --build-arg DIST_JAR_NAME=$(shell basename $(DIST_JAR_NAME)) \
		-t $(HUB)$(shell echo $(NAME) | tr A-Z a-z):$(TAG) \
		-t $(HUB)$(shell echo $(NAME) | tr A-Z a-z):latest \
		$(DOCKER_BUILD_TOP)/$(NAME)
	docker buildx rm nt_builder || true
endef