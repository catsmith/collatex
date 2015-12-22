#!/usr/bin/env python
# -*- coding: utf-8 -*-

__author__ = 'Ronald Haentjens Dekker'
__email__ = 'ronald.dekker@huygens.knaw.nl'
__version__ = '2.0.0rc16'

from collatex.core_functions import Collation
from collatex.core_functions import collate
from collatex.core_functions import collate_pretokenized_json
from collatex.core_functions import collate_nearMatch
from collatex.core_functions import collate_pretokenized_json_nearMatch

__all__ = ["Collation", "collate", "collate_pretokenized_json", "collate_nearMatch", "collate_pretokenized_json_nearMatch"]


